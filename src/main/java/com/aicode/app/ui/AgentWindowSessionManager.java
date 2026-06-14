package com.aicode.app.ui;

import com.aicode.agent.TurnContext;
import com.aicode.app.application.AgentApplication;
import com.aicode.app.config.AppConfig;
import com.aicode.app.config.ModelProfile;
import com.aicode.app.event.AgentEvent;
import com.aicode.app.session.AgentSession;
import com.aicode.app.session.AgentSessionService;
import com.aicode.app.session.ChatMode;
import com.aicode.app.ui.dialog.ApprovalDialog;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Multi-workspace, multi-conversation session manager for the Agent window. */
public final class AgentWindowSessionManager {
    private final Supplier<Stage> stageSupplier;
    private final ChatTranscriptView chatView;
    private final ChatComposerInput chatInput;
    private final Button sendButton;
    private final ComboBox<ChatMode> chatModeBox;
    private final Label threadTitleLabel;
    private final Label footerLabel;
    private final Runnable onMissingModel;

    private final List<WorkspaceContext> workspaces = new ArrayList<>();
    private WorkspaceContext activeWorkspace;
    private ConversationContext activeConversation;
    private AgentSidebarNav sidebarNav;
    private ModelProfile activeModel;
    private final StreamingChatAppender streamingChat;
    private Consumer<Path> onWorkspaceActivated;
    private Runnable onMessageSent;

    public AgentWindowSessionManager(
            Supplier<Stage> stageSupplier,
            ChatTranscriptView chatView,
            ChatComposerInput chatInput,
            Button sendButton,
            ComboBox<ChatMode> chatModeBox,
            Label threadTitleLabel,
            Label footerLabel,
            Runnable onMissingModel
    ) {
        this.stageSupplier = stageSupplier;
        this.chatView = chatView;
        this.chatInput = chatInput;
        this.sendButton = sendButton;
        this.chatModeBox = chatModeBox;
        this.threadTitleLabel = threadTitleLabel;
        this.footerLabel = footerLabel;
        this.onMissingModel = onMissingModel;
        this.streamingChat = new StreamingChatAppender(chatView);
    }

    public void setOnWorkspaceActivated(Consumer<Path> onWorkspaceActivated) {
        this.onWorkspaceActivated = onWorkspaceActivated;
    }

    public void setOnMessageSent(Runnable onMessageSent) {
        this.onMessageSent = onMessageSent;
    }

    public Path activeWorkspacePath() {
        return activeWorkspace != null ? activeWorkspace.path() : null;
    }

    public void bindSidebarNav(AgentSidebarNav nav) {
        this.sidebarNav = nav;
        nav.setOnAddWorkspace(this::addWorkspaceRequested);
        nav.setOnSelectWorkspace(this::selectWorkspace);
        nav.setOnSelectConversation((workspace, conversation) -> {
            if (activeWorkspace != workspace) {
                activeWorkspace = workspace;
                footerLabel.setText(workspace.path().toString());
            }
            switchConversation(conversation);
        });
        nav.setOnAddConversation(workspace -> {
            if (activeWorkspace != workspace) {
                selectWorkspace(workspace);
            }
            createConversationIn(workspace);
        });
        nav.setOnCloseConversation(this::closeConversation);
    }

    private void refreshSidebarNav() {
        if (sidebarNav != null) {
            sidebarNav.render(workspaces, activeWorkspace, activeConversation);
        }
    }

    public void initializeModes() {
        chatModeBox.getItems().addAll(ChatMode.AGENT, ChatMode.CHAT);
        chatModeBox.setValue(ChatMode.AGENT);
        chatModeBox.valueProperty().addListener((obs, old, mode) -> updateModeHint(mode));
        updateModeHint(ChatMode.AGENT);
        sendButton.setOnAction(e -> handleSendOrStop());
        chatInput.setOnSubmit(() -> {
            if (activeConversation == null || !activeConversation.generating()) {
                sendMessage();
            }
        });
        sendButton.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                return;
            }
            javafx.stage.Window window = scene.getWindow();
            if (window instanceof Stage stage) {
                stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, event -> flushActiveSessions());
            }
        });
        chatView.setOnLoadOlder(() -> {
            if (activeConversation != null && activeWorkspace != null) {
                loadOlderTurns(activeConversation);
            }
        });
    }

    private void flushActiveSessions() {
        for (WorkspaceContext workspace : workspaces) {
            if (workspace.sessionService() != null) {
                workspace.sessionService().flushAllSessions();
            }
        }
    }

    private void handleSendOrStop() {
        if (activeConversation != null && activeConversation.generating()) {
            stopGenerating();
        } else {
            sendMessage();
        }
    }

    private void stopGenerating() {
        if (activeWorkspace == null || activeConversation == null || activeWorkspace.sessionService() == null) {
            return;
        }
        activeWorkspace.sessionService().cancelSession(activeConversation.sessionId());
    }

    private void setGeneratingUi(boolean generating) {
        sendButton.setText(generating ? "■" : "↑");
        sendButton.setDisable(false);
    }

    public void addInitialWorkspace(Path path) {
        if (workspaces.stream().anyMatch(ws -> ws.path().equals(path))) {
            return;
        }
        WorkspaceContext workspace = new WorkspaceContext(path);
        workspaces.add(workspace);
        refreshSidebarNav();
        initWorkspaceService(workspace);
        restoreOrCreateConversations(workspace);
        selectWorkspace(workspace);
    }

    public void applyModel(ModelProfile model) {
        this.activeModel = model;
        for (WorkspaceContext workspace : workspaces) {
            initWorkspaceService(workspace);
        }
        for (WorkspaceContext workspace : workspaces) {
            if (workspace.isReady() && shouldRestoreFromDisk(workspace)) {
                workspace.clearConversations();
                restoreOrCreateConversations(workspace);
            } else if (workspace.isReady()) {
                for (ConversationContext conversation : workspace.conversations()) {
                    if (conversation == activeConversation) {
                        presentConversationFromDisk(workspace, conversation);
                    }
                }
            }
        }
        if (activeWorkspace != null) {
            if (activeConversation == null && !activeWorkspace.conversations().isEmpty()) {
                switchConversation(activeWorkspace.conversations().getFirst());
            } else {
                updateInputState();
            }
        } else if (!workspaces.isEmpty()) {
            selectWorkspace(workspaces.getFirst());
        } else {
            updateInputState();
        }
    }

    public void appendSystemLine(String text) {
        if (activeConversation == null) {
            return;
        }
        appendStandaloneNotice(activeConversation, text);
    }

    private void addWorkspaceRequested() {
        Stage stage = stageSupplier.get();
        Path selected = com.aicode.app.window.WindowManager.chooseWorkspace(stage);
        if (selected == null) {
            return;
        }
        Path normalized = com.aicode.app.config.WorkingDirectory.normalizeWorkspace(selected);
        if (workspaces.stream().anyMatch(ws -> ws.path().equals(normalized))) {
            WorkspaceContext existing = workspaces.stream()
                    .filter(ws -> ws.path().equals(normalized))
                    .findFirst()
                    .orElseThrow();
            selectWorkspace(existing);
            return;
        }
        WorkspaceContext workspace = new WorkspaceContext(normalized);
        workspaces.add(workspace);
        refreshSidebarNav();
        initWorkspaceService(workspace);
        restoreOrCreateConversations(workspace);
        selectWorkspace(workspace);
    }

    private void initWorkspaceService(WorkspaceContext workspace) {
        if (activeModel == null || !activeModel.isUsable()) {
            flushWorkspaceSessions(workspace);
            workspace.setApplication(null);
            workspace.setSessionService(null);
            return;
        }
        try {
            AgentSessionService previousService = workspace.sessionService();
            if (previousService != null) {
                previousService.flushAllSessions();
            }
            AppConfig config = activeModel.toAppConfig(workspace.path());
            AgentApplication application = new AgentApplication(config);
            AgentSessionService sessionService = new AgentSessionService(application, false);
            workspace.setApplication(application);
            workspace.setSessionService(sessionService);
            rebindWorkspaceConversations(workspace, previousService);
        } catch (RuntimeException e) {
            flushWorkspaceSessions(workspace);
            workspace.setApplication(null);
            workspace.setSessionService(null);
            if (workspace == activeWorkspace) {
                chatView.showPlainError("Agent 初始化失败: " + e.getMessage());
            }
        }
    }

    private void flushWorkspaceSessions(WorkspaceContext workspace) {
        AgentSessionService service = workspace.sessionService();
        if (service != null) {
            service.flushAllSessions();
        }
    }

    private void rebindWorkspaceConversations(WorkspaceContext workspace, AgentSessionService previousService) {
        AgentSessionService sessionService = workspace.sessionService();
        if (sessionService == null) {
            return;
        }
        for (ConversationContext conversation : workspace.conversations()) {
            AgentSession session = sessionService.migrateSession(
                    conversation.sessionId(),
                    workspace.path(),
                    previousService,
                    conversation.transcript(),
                    conversation.title()
            );
            conversation.rebindSession(session.sessionId(), buildBridge(conversation));
        }
    }

    private void selectWorkspace(WorkspaceContext workspace) {
        activeWorkspace = workspace;
        footerLabel.setText(workspace.path().toString());
        if (workspace.conversations().isEmpty()) {
            if (workspace.isReady()) {
                restoreOrCreateConversations(workspace);
            }
            if (workspace.conversations().isEmpty()) {
                createConversationIn(workspace);
            }
        } else if (activeConversation == null
                || !workspace.conversations().contains(activeConversation)) {
            switchConversation(workspace.conversations().getFirst());
        } else {
            switchConversation(activeConversation);
        }
        refreshSidebarNav();
        notifyWorkspaceActivated(workspace.path());
        updateInputState();
    }

    private void restoreOrCreateConversations(WorkspaceContext workspace) {
        AgentSessionService service = workspace.sessionService();
        if (service == null || !workspace.conversations().isEmpty()) {
            return;
        }
        List<com.aicode.app.session.SessionPersistence.StoredSession> stored =
                service.loadStoredSessions(workspace.path());
        if (stored.isEmpty()) {
            createConversationIn(workspace);
            return;
        }
        for (com.aicode.app.session.SessionPersistence.StoredSession saved : stored) {
            com.aicode.app.session.AgentSession session = service.restoreSession(saved);
            ConversationContext conversation = new ConversationContext(session.sessionId(), saved.title());
            conversation.setBridge(buildBridge(conversation));
            loadRecentTranscript(workspace, conversation);
            workspace.addConversation(conversation);
        }
    }

    private void closeConversation(WorkspaceContext workspace, ConversationContext conversation) {
        if (workspace.sessionService() == null) {
            return;
        }
        workspace.sessionService().closeSession(conversation.sessionId());
        workspace.removeConversation(conversation);
        if (activeConversation == conversation) {
            streamingChat.resetPending();
            activeConversation = null;
            chatView.clear();
            if (!workspace.conversations().isEmpty()) {
                switchConversation(workspace.conversations().getFirst());
            } else if (workspace == activeWorkspace) {
                createConversationIn(workspace);
            }
        }
        refreshSidebarNav();
        updateInputState();
    }

    private void createConversationIn(WorkspaceContext workspace) {
        if (workspace.sessionService() == null) {
            return;
        }
        int count = workspace.conversations().size() + 1;
        String title = "新对话 " + count;
        AgentSession session = workspace.sessionService().createSession(workspace.path());
        workspace.sessionService().setSessionTitle(session.sessionId(), title);
        ConversationContext conversation = new ConversationContext(session.sessionId(), title);
        conversation.setBridge(buildBridge(conversation));
        workspace.addConversation(conversation);
        if (workspace == activeWorkspace) {
            switchConversation(conversation);
        } else {
            refreshSidebarNav();
        }
    }

    private UiAgentBridge buildBridge(ConversationContext conversation) {
        return new UiAgentBridge(
                text -> appendStreamToConversation(conversation, text),
                text -> appendActivity(conversation, text),
                (event, onComplete) -> showApproval(conversation, event, onComplete)
        );
    }

    private void switchConversation(ConversationContext conversation) {
        streamingChat.resetPending();
        activeConversation = conversation;
        if (activeWorkspace != null) {
            presentConversationFromDisk(activeWorkspace, conversation);
        }
        threadTitleLabel.setText(conversation.generating() ? "生成中..." : conversation.title());
        refreshSidebarNav();
        if (activeWorkspace != null) {
            notifyWorkspaceActivated(activeWorkspace.path());
        }
        updateInputState();
    }

    private void presentConversationFromDisk(WorkspaceContext workspace, ConversationContext conversation) {
        if (workspace.sessionService() == null) {
            chatView.clear();
            return;
        }
        ChatMode mode = chatModeBox.getValue() != null ? chatModeBox.getValue() : ChatMode.AGENT;
        com.aicode.app.session.SessionPersistence.HistoryPage page =
                com.aicode.app.session.ConversationTranscriptLoader.loadRecentPage(
                        conversation.transcript(),
                        workspace.sessionService(),
                        workspace.path(),
                        conversation.sessionId(),
                        mode
                );
        conversation.setTranscriptPagination(page.startIndex(), page.totalTurns());
        chatView.loadTurns(conversation.transcript().turns());
        chatView.setHasOlderTurns(page.hasOlder());
    }

    private void loadRecentTranscript(WorkspaceContext workspace, ConversationContext conversation) {
        if (workspace.sessionService() == null) {
            return;
        }
        ChatMode mode = chatModeBox.getValue() != null ? chatModeBox.getValue() : ChatMode.AGENT;
        com.aicode.app.session.SessionPersistence.HistoryPage page =
                com.aicode.app.session.ConversationTranscriptLoader.loadRecentPage(
                        conversation.transcript(),
                        workspace.sessionService(),
                        workspace.path(),
                        conversation.sessionId(),
                        mode
                );
        conversation.setTranscriptPagination(page.startIndex(), page.totalTurns());
    }

    private void loadOlderTurns(ConversationContext conversation) {
        if (activeWorkspace == null || activeWorkspace.sessionService() == null
                || conversation.loadingOlderTurns() || !conversation.hasOlderTurns()) {
            chatView.finishLoadingOlderTurns();
            return;
        }
        conversation.setLoadingOlderTurns(true);
        ChatMode mode = chatModeBox.getValue() != null ? chatModeBox.getValue() : ChatMode.AGENT;
        com.aicode.app.session.SessionPersistence.HistoryPage page =
                com.aicode.app.session.ConversationTranscriptLoader.loadOlderPage(
                        conversation.transcript(),
                        activeWorkspace.sessionService(),
                        activeWorkspace.path(),
                        conversation.sessionId(),
                        mode,
                        conversation.oldestLoadedTurnIndex()
                );
        conversation.setTranscriptPagination(page.startIndex(), page.totalTurns());
        List<ChatTurn> prepended = conversation.transcript().turns().subList(0, page.turns().size());
        chatView.prependTurns(prepended, page.hasOlder());
        conversation.setLoadingOlderTurns(false);
        chatView.finishLoadingOlderTurns();
    }

    private void notifyWorkspaceActivated(Path path) {
        if (onWorkspaceActivated != null) {
            onWorkspaceActivated.accept(path);
        }
    }

    private void sendMessage() {
        if (activeModel == null || !activeModel.isUsable()
                || activeWorkspace == null
                || activeWorkspace.sessionService() == null
                || activeConversation == null) {
            onMissingModel.run();
            return;
        }
        if (activeConversation.generating()) {
            return;
        }
        String text = chatInput.getText().strip();
        if (text.isEmpty()) {
            return;
        }
        if (onMessageSent != null) {
            onMessageSent.run();
        }
        String payload = chatInput.attachments().buildFullPrompt(text, activeWorkspace.path());
        TurnContext turnContext = TurnContext.of(activeWorkspace.path(), chatInput.activeFile());
        ChatMode mode = chatModeBox.getValue() != null ? chatModeBox.getValue() : ChatMode.AGENT;
        ConversationContext sending = activeConversation;
        chatInput.clearAfterSend();
        maybeUpdateTitle(sending, text);
        appendUser(sending, text);
        sending.setTranscriptPagination(
                sending.oldestLoadedTurnIndex(),
                sending.totalTurns() + 1
        );
        streamingChat.beginStream();
        sending.setGenerating(true);
        setGeneratingUi(true);
        threadTitleLabel.setText("生成中...");

        AgentSessionService sessionService = activeWorkspace.sessionService();
        if (mode == ChatMode.CHAT) {
            sessionService.sendChatMessage(sending.sessionId(), payload, text, sending.bridge(), turnContext)
                    .whenComplete((result, error) -> Platform.runLater(() -> finishSend(sending, error)));
        } else {
            sessionService.sendAgentMessage(sending.sessionId(), payload, text, sending.bridge(), turnContext)
                    .whenComplete((result, error) -> Platform.runLater(() -> finishSend(sending, error)));
        }
    }

    private void finishSend(ConversationContext conversation, Throwable error) {
        Runnable done = () -> {
            conversation.setGenerating(false);
            if (conversation == activeConversation) {
                updateInputState();
                threadTitleLabel.setText(conversation.title());
            }
            refreshSidebarNav();
            if (error != null) {
                appendActivity(conversation, "Error: " + error.getMessage());
            }
        };
        if (conversation == activeConversation) {
            streamingChat.finishStream(done);
        } else {
            done.run();
        }
    }

    private void maybeUpdateTitle(ConversationContext conversation, String userMessage) {
        if (!conversation.title().startsWith("新对话")) {
            return;
        }
        String shortTitle = userMessage.length() > 28
                ? userMessage.substring(0, 28) + "…"
                : userMessage;
        conversation.setTitle(shortTitle);
        if (activeWorkspace != null && activeWorkspace.sessionService() != null) {
            activeWorkspace.sessionService().setSessionTitle(conversation.sessionId(), shortTitle);
        }
        refreshSidebarNav();
        if (conversation == activeConversation) {
            threadTitleLabel.setText(shortTitle);
        }
    }

    private void updateModeHint(ChatMode mode) {
        if (mode == null) {
            return;
        }
        chatInput.setPromptText(mode == ChatMode.CHAT
                ? "发送后续消息（问答模式）..."
                : "发送后续消息...");
        if (activeConversation != null && activeWorkspace != null) {
            presentConversationFromDisk(activeWorkspace, activeConversation);
        }
    }

    private boolean shouldRestoreFromDisk(WorkspaceContext workspace) {
        if (workspace.conversations().isEmpty()) {
            return !workspace.sessionService().loadStoredSessions(workspace.path()).isEmpty();
        }
        return false;
    }

    private void updateInputState() {
        boolean ready = activeModel != null && activeModel.isUsable()
                && activeWorkspace != null
                && activeWorkspace.isReady()
                && activeConversation != null;
        if (activeConversation != null && activeConversation.generating()) {
            chatInput.disableInput(!ready);
            setGeneratingUi(true);
            return;
        }
        chatInput.disableInput(!ready);
        sendButton.setText("↑");
        sendButton.setDisable(!ready);
    }

    private void appendUser(ConversationContext conversation, String text) {
        String createdAt = java.time.Instant.now().toString();
        conversation.transcript().startTurn(text, createdAt);
        if (conversation == activeConversation) {
            Platform.runLater(() -> chatView.startTurn(text, createdAt));
        }
    }

    private void appendActivity(ConversationContext conversation, String text) {
        conversation.transcript().addActivity(text);
        if (conversation == activeConversation) {
            var activities = conversation.transcript().openTurn().activities();
            Platform.runLater(() -> chatView.updateCurrentTurnActivity(new java.util.ArrayList<>(activities)));
        }
    }

    private void appendStandaloneNotice(ConversationContext conversation, String text) {
        conversation.transcript().addStandaloneNotice(text);
        if (conversation == activeConversation) {
            Platform.runLater(() -> chatView.appendStandaloneNotice(text));
        }
    }

    private void appendStreamToConversation(ConversationContext conversation, String text) {
        conversation.transcript().appendAssistant(text);
        if (conversation == activeConversation) {
            streamingChat.append(text);
        }
    }

    private void showApproval(
            ConversationContext conversation,
            AgentEvent.ApprovalRequired event,
            Runnable onComplete
    ) {
        Platform.runLater(() -> {
            WorkspaceContext workspace = findWorkspaceFor(conversation);
            if (workspace == null || workspace.sessionService() == null) {
                return;
            }
            Stage stage = stageSupplier.get();
            ApprovalDialog dialog = new ApprovalDialog(stage, event, approved -> {
                workspace.sessionService().resolveApproval(
                        conversation.sessionId(),
                        event.approvalId(),
                        approved
                );
                onComplete.run();
            });
            dialog.show();
        });
    }

    private WorkspaceContext findWorkspaceFor(ConversationContext conversation) {
        for (WorkspaceContext workspace : workspaces) {
            if (workspace.conversations().contains(conversation)) {
                return workspace;
            }
        }
        return activeWorkspace;
    }
}

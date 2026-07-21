package com.aicode.app.ui;

import com.aicode.agent.TurnContext;
import com.aicode.app.application.AgentApplication;
import com.aicode.app.config.AppConfig;
import com.aicode.app.config.ModelProfile;
import com.aicode.app.config.WorkingDirectory;
import com.aicode.app.event.AgentEvent;
import com.aicode.app.session.AgentSession;
import com.aicode.app.session.AgentSessionService;
import com.aicode.app.session.ChatMode;
import com.aicode.app.session.UserMessagePayload;
import com.aicode.app.session.ConversationTranscriptLoader;
import com.aicode.app.session.SessionPersistence;
import com.aicode.app.ui.dialog.ApprovalDialog;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Shared agent/chat session wiring for project and agents windows. */
public final class AgentSessionBinder {
    private final Supplier<Stage> stageSupplier;
    private final ChatTranscriptView chatView;
    private final ChatComposerInput chatInput;
    private final Button sendButton;
    private final ComboBox<ChatMode> chatModeBox;
    private final Label statusLabel;
    private Path workspaceRoot;
    private final Runnable onMissingModel;
    private final Supplier<String> idleStatusText;
    private final boolean statusShowsConversationTitle;

    private AgentApplication application;
    private AgentSessionService sessionService;
    private final List<ConversationContext> conversations = new ArrayList<>();
    private ConversationContext active;
    private ListView<ConversationContext> conversationList;
    private StreamingChatAppender streamingChat;
    private ModelProfile activeModel;
    private Consumer<String> toolLogConsumer;
    private Consumer<Path> onFileDiskChanged = path -> {};

    public AgentSessionBinder(
            Path workspaceRoot,
            Supplier<Stage> stageSupplier,
            ChatTranscriptView chatView,
            ChatComposerInput chatInput,
            Button sendButton,
            ComboBox<ChatMode> chatModeBox,
            Label statusLabel,
            Runnable onMissingModel
    ) {
        this(workspaceRoot, stageSupplier, chatView, chatInput, sendButton,
                chatModeBox, statusLabel, onMissingModel, null, false);
    }

    public AgentSessionBinder(
            Path workspaceRoot,
            Supplier<Stage> stageSupplier,
            ChatTranscriptView chatView,
            ChatComposerInput chatInput,
            Button sendButton,
            ComboBox<ChatMode> chatModeBox,
            Label statusLabel,
            Runnable onMissingModel,
            Supplier<String> idleStatusText
    ) {
        this(workspaceRoot, stageSupplier, chatView, chatInput, sendButton,
                chatModeBox, statusLabel, onMissingModel, idleStatusText, false);
    }

    public AgentSessionBinder(
            Path workspaceRoot,
            Supplier<Stage> stageSupplier,
            ChatTranscriptView chatView,
            ChatComposerInput chatInput,
            Button sendButton,
            ComboBox<ChatMode> chatModeBox,
            Label statusLabel,
            Runnable onMissingModel,
            Supplier<String> idleStatusText,
            boolean statusShowsConversationTitle
    ) {
        this.workspaceRoot = workspaceRoot;
        this.stageSupplier = stageSupplier;
        this.chatView = chatView;
        this.chatInput = chatInput;
        this.sendButton = sendButton;
        this.chatModeBox = chatModeBox;
        this.statusLabel = statusLabel;
        this.onMissingModel = onMissingModel;
        this.idleStatusText = idleStatusText != null
                ? idleStatusText
                : () -> "Workspace: " + workspaceRoot;
        this.statusShowsConversationTitle = statusShowsConversationTitle;
        this.streamingChat = new StreamingChatAppender(chatView);
        chatView.setOnFileEditResolved((editId, kept) -> {
            if (sessionService != null && active != null) {
                sessionService.resolveFileEdit(active.sessionId(), editId, kept);
            }
        });
    }

    public void setOnFileEditChanged(Consumer<Path> onFileEditChanged) {
        chatView.setOnFileEditChanged(onFileEditChanged);
    }

    public void setOnFileDiskChanged(Consumer<Path> onFileDiskChanged) {
        this.onFileDiskChanged = onFileDiskChanged != null ? onFileDiskChanged : path -> {};
    }

    public void setToolLogConsumer(Consumer<String> toolLogConsumer) {
        this.toolLogConsumer = toolLogConsumer;
    }

    public void bindConversations(ListView<ConversationContext> listView, Button newButton) {
        this.conversationList = listView;
        listView.setCellFactory(lv -> new ListCell<>() {
            private final HBox row = new HBox(6);
            private final Label titleLabel = new Label();
            private final Region spacer = new Region();
            private final Button closeButton = new Button("×");

            {
                HBox.setHgrow(spacer, Priority.ALWAYS);
                closeButton.getStyleClass().add("conversation-close");
                closeButton.setOnAction(e -> {
                    ConversationContext item = getItem();
                    if (item != null) {
                        closeConversation(item);
                    }
                });
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                row.getChildren().addAll(titleLabel, spacer, closeButton);
            }

            @Override
            protected void updateItem(ConversationContext item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                titleLabel.setText(item.title());
                setGraphic(row);
            }
        });
        listView.getItems().setAll(conversations);
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected == null || selected == active) {
                return;
            }
            switchConversation(selected);
        });
        newButton.setOnAction(e -> createNewConversation());
    }

    public void initializeModes() {
        chatModeBox.getItems().addAll(ChatMode.AGENT, ChatMode.CHAT);
        chatModeBox.setValue(ChatMode.AGENT);
        chatModeBox.valueProperty().addListener((obs, old, mode) -> updateModeHint(mode));
        updateModeHint(ChatMode.AGENT);
        sendButton.setOnAction(e -> handleSendOrStop());
        chatInput.setOnSubmit(() -> {
            if (active == null || !active.generating()) {
                sendMessage();
            }
        });
        sendButton.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                return;
            }
            javafx.stage.Window window = scene.getWindow();
            if (window instanceof Stage stage) {
                stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST, event -> {
                    if (sessionService != null) {
                        sessionService.flushAllSessions();
                    }
                });
            }
        });
        chatView.setOnLoadOlder(() -> {
            if (active != null) {
                loadOlderTurns(active);
            }
        });
    }

    private void handleSendOrStop() {
        if (active != null && active.generating()) {
            stopGenerating();
        } else {
            sendMessage();
        }
    }

    private void stopGenerating() {
        if (sessionService == null || active == null) {
            return;
        }
        sessionService.cancelSession(active.sessionId());
    }

    private void setGeneratingUi(boolean generating) {
        sendButton.setText(generating ? "■" : "↑");
        sendButton.setDisable(false);
    }

    public void updateWorkspace(Path workspace) {
        this.workspaceRoot = WorkingDirectory.normalizeWorkspace(workspace);
        refreshAgentState();
    }

    public void applyModel(ModelProfile model) {
        this.activeModel = model;
        refreshAgentState();
    }

    public void appendSystemLine(String text) {
        if (active == null) {
            return;
        }
        appendStandaloneNotice(active, text);
    }

    public void refreshAgentState() {
        if (sessionService != null) {
            sessionService.flushAllSessions();
        }
        conversations.clear();
        active = null;
        if (conversationList != null) {
            conversationList.getItems().clear();
        }
        chatView.clear();

        if (activeModel != null && activeModel.isUsable()) {
            try {
                AppConfig config = activeModel.toAppConfig(workspaceRoot);
                application = new AgentApplication(config);
                sessionService = new AgentSessionService(application, false);
                restoreOrCreateConversations();
                updateInputState();
            } catch (RuntimeException e) {
                sendButton.setDisable(true);
                chatInput.disableInput(true);
                chatView.showPlainError("Agent 初始化失败: " + e.getMessage());
            }
        } else {
            application = null;
            sessionService = null;
            sendButton.setDisable(true);
            chatInput.disableInput(true);
        }
    }

    private void restoreOrCreateConversations() {
        if (sessionService == null) {
            return;
        }
        List<SessionPersistence.StoredSession> stored =
                sessionService.loadStoredSessions(workspaceRoot);
        if (stored.isEmpty()) {
            createNewConversation();
            return;
        }
        for (SessionPersistence.StoredSession saved : stored) {
            AgentSession session = sessionService.restoreSession(saved);
            ConversationContext context = buildConversation(session.sessionId(), saved.title());
            loadRecentTranscript(context);
            conversations.add(context);
        }
        if (conversationList != null) {
            conversationList.getItems().setAll(conversations);
        }
        switchConversation(conversations.getFirst());
    }

    public void closeConversation(ConversationContext context) {
        if (sessionService == null || context == null) {
            return;
        }
        sessionService.closeSession(context.sessionId());
        conversations.remove(context);
        if (conversationList != null) {
            conversationList.getItems().setAll(conversations);
        }
        if (active == context) {
            streamingChat.resetPending();
            active = null;
            chatView.clear();
            if (!conversations.isEmpty()) {
                switchConversation(conversations.getFirst());
            } else {
                createNewConversation();
            }
        }
    }

    public void createNewConversation() {
        if (sessionService == null) {
            return;
        }
        String title = "新对话 " + (conversations.size() + 1);
        AgentSession session = sessionService.createSession(workspaceRoot);
        sessionService.setSessionTitle(session.sessionId(), title);
        ConversationContext context = buildConversation(session.sessionId(), title);
        conversations.add(context);
        if (conversationList != null) {
            conversationList.getItems().setAll(conversations);
        }
        switchConversation(context);
    }

    private ConversationContext buildConversation(String sessionId, String title) {
        ConversationContext context = new ConversationContext(sessionId, title);
        context.setBridge(new UiAgentBridge(
                text -> appendStreamToConversation(context, text),
                text -> {
                    appendActivity(context, text);
                    if (toolLogConsumer != null) {
                        toolLogConsumer.accept(text);
                    }
                },
                (event, onComplete) -> showApproval(context, event, onComplete),
                proposal -> chatView.showFileEditReview(proposal),
                onFileDiskChanged
        ));
        return context;
    }

    private void switchConversation(ConversationContext context) {
        streamingChat.resetPending();
        active = context;
        presentConversationFromDisk(context);
        if (statusShowsConversationTitle) {
            statusLabel.setText(context.generating() ? "生成中..." : context.title());
        }
        if (conversationList != null) {
            conversationList.getSelectionModel().select(context);
        }
        updateInputState();
    }

    private void presentConversationFromDisk(ConversationContext context) {
        if (sessionService == null) {
            chatView.clear();
            return;
        }
        ChatMode mode = chatModeBox.getValue() != null ? chatModeBox.getValue() : ChatMode.AGENT;
        SessionPersistence.HistoryPage page = ConversationTranscriptLoader.loadRecentPage(
                context.transcript(),
                sessionService,
                workspaceRoot,
                context.sessionId(),
                mode
        );
        context.setTranscriptPagination(page.startIndex(), page.totalTurns());
        chatView.loadTurns(context.transcript().turns());
        chatView.setHasOlderTurns(page.hasOlder());
    }

    private void loadOlderTurns(ConversationContext context) {
        if (sessionService == null || context.loadingOlderTurns() || !context.hasOlderTurns()) {
            chatView.finishLoadingOlderTurns();
            return;
        }
        context.setLoadingOlderTurns(true);
        ChatMode mode = chatModeBox.getValue() != null ? chatModeBox.getValue() : ChatMode.AGENT;
        SessionPersistence.HistoryPage page = ConversationTranscriptLoader.loadOlderPage(
                context.transcript(),
                sessionService,
                workspaceRoot,
                context.sessionId(),
                mode,
                context.oldestLoadedTurnIndex()
        );
        context.setTranscriptPagination(page.startIndex(), page.totalTurns());
        List<ChatTurn> prepended = context.transcript().turns().subList(0, page.turns().size());
        chatView.prependTurns(prepended, page.hasOlder());
        context.setLoadingOlderTurns(false);
        chatView.finishLoadingOlderTurns();
    }

    private void sendMessage() {
        if (activeModel == null || !activeModel.isUsable() || sessionService == null || active == null) {
            onMissingModel.run();
            return;
        }
        if (active.generating()) {
            return;
        }
        String text = chatInput.getText().strip();
        if (text.isEmpty() && !chatInput.attachments().hasImages()) {
            return;
        }
        UserMessagePayload userPayload = chatInput.attachments().buildUserMessage(text, workspaceRoot);
        TurnContext turnContext = TurnContext.of(workspaceRoot, chatInput.activeFile());
        ChatMode mode = chatModeBox.getValue() != null ? chatModeBox.getValue() : ChatMode.AGENT;
        ConversationContext sending = active;
        chatInput.clearAfterSend();
        maybeUpdateTitle(sending, text);
        appendUser(sending, userPayload);
        sending.setTranscriptPagination(
                sending.oldestLoadedTurnIndex(),
                sending.totalTurns() + 1
        );
        streamingChat.beginStream();
        sending.setGenerating(true);
        setGeneratingUi(true);
        statusLabel.setText("生成中...");

        if (mode == ChatMode.CHAT) {
            sessionService.sendChatMessage(sending.sessionId(), userPayload, sending.bridge(), turnContext)
                    .whenComplete((result, error) -> Platform.runLater(() -> finishSend(sending, error)));
        } else {
            sessionService.sendAgentMessage(sending.sessionId(), userPayload, sending.bridge(), turnContext)
                    .whenComplete((result, error) -> Platform.runLater(() -> finishSend(sending, error)));
        }
    }

    private void finishSend(ConversationContext context, Throwable error) {
        Runnable done = () -> {
            context.setGenerating(false);
            if (context == active) {
                updateInputState();
                if (statusShowsConversationTitle) {
                    statusLabel.setText(context.title());
                } else {
                    statusLabel.setText(idleStatusText.get());
                }
            }
            if (conversationList != null) {
                conversationList.refresh();
            }
            if (error != null) {
                appendActivity(context, "Error: " + error.getMessage());
            }
        };
        if (context == active) {
            streamingChat.finishStream(done);
        } else {
            done.run();
        }
    }

    private void maybeUpdateTitle(ConversationContext context, String userMessage) {
        if (!context.title().startsWith("新对话")) {
            return;
        }
        String shortTitle = userMessage.length() > 28
                ? userMessage.substring(0, 28) + "…"
                : userMessage;
        context.setTitle(shortTitle);
        if (sessionService != null) {
            sessionService.setSessionTitle(context.sessionId(), shortTitle);
        }
        if (conversationList != null) {
            conversationList.refresh();
        }
        if (context == active && statusShowsConversationTitle) {
            statusLabel.setText(shortTitle);
        }
    }

    private void updateInputState() {
        boolean ready = activeModel != null && activeModel.isUsable()
                && sessionService != null && active != null;
        if (active != null && active.generating()) {
            chatInput.disableInput(!ready);
            setGeneratingUi(true);
            return;
        }
        chatInput.disableInput(!ready);
        sendButton.setText("↑");
        sendButton.setDisable(!ready);
    }

    private void updateModeHint(ChatMode mode) {
        if (mode == null) {
            return;
        }
        chatInput.setPromptText(mode == ChatMode.CHAT
                ? "发送后续消息（问答模式）..."
                : "发送后续消息...");
        if (active != null) {
            presentConversationFromDisk(active);
        }
    }

    private void loadRecentTranscript(ConversationContext context) {
        if (sessionService == null) {
            return;
        }
        ChatMode mode = chatModeBox.getValue() != null ? chatModeBox.getValue() : ChatMode.AGENT;
        SessionPersistence.HistoryPage page = ConversationTranscriptLoader.loadRecentPage(
                context.transcript(),
                sessionService,
                workspaceRoot,
                context.sessionId(),
                mode
        );
        context.setTranscriptPagination(page.startIndex(), page.totalTurns());
    }

    private void appendUser(ConversationContext context, UserMessagePayload payload) {
        String text = payload.displayText() != null ? payload.displayText() : "";
        List<String> imagePaths = payload.imagePaths();
        String createdAt = java.time.Instant.now().toString();
        context.transcript().startTurn(text, createdAt, imagePaths);
        if (context == active) {
            Platform.runLater(() -> chatView.startTurn(text, createdAt, imagePaths));
        }
    }

    private void appendActivity(ConversationContext context, String text) {
        context.transcript().addActivity(text);
        if (context == active) {
            var activities = context.transcript().openTurn().activities();
            Platform.runLater(() -> chatView.updateCurrentTurnActivity(new java.util.ArrayList<>(activities)));
        }
    }

    private void appendStandaloneNotice(ConversationContext context, String text) {
        context.transcript().addStandaloneNotice(text);
        if (context == active) {
            Platform.runLater(() -> chatView.appendStandaloneNotice(text));
        }
    }

    private void appendStreamToConversation(ConversationContext context, String text) {
        context.transcript().appendAssistant(text);
        if (context == active) {
            streamingChat.append(text);
        }
    }

    private void showApproval(
            ConversationContext context,
            AgentEvent.ApprovalRequired event,
            Runnable onComplete
    ) {
        Platform.runLater(() -> {
            Stage stage = stageSupplier.get();
            ApprovalDialog dialog = new ApprovalDialog(stage, event, approved -> {
                sessionService.resolveApproval(context.sessionId(), event.approvalId(), approved);
                onComplete.run();
            });
            dialog.show();
        });
    }
}

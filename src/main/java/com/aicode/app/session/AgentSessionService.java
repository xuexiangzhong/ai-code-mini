package com.aicode.app.session;

import com.aicode.agent.Agent;
import com.aicode.agent.Compressor;
import com.aicode.agent.Context;
import com.aicode.agent.HistoryCompactor;
import com.aicode.agent.Markdown;
import com.aicode.agent.MessageHistory;
import com.aicode.agent.ToolMessageRepair;
import com.aicode.agent.TurnContext;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.StreamingChatHelper;
import com.aicode.app.application.AgentApplication;
import com.aicode.app.approval.ApprovalGate;
import com.aicode.app.approval.CliApprovalHandler;
import com.aicode.app.approval.FileEditGate;
import com.aicode.app.config.WorkingDirectory;
import com.aicode.app.event.AgentEvent;
import com.aicode.app.event.AgentEventListener;
import com.aicode.app.ui.ConversationTranscript;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentSessionService {
    private final AgentApplication application;
    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ApprovalGate> approvalGates = new ConcurrentHashMap<>();
    private final Map<String, FileEditGate> fileEditGates = new ConcurrentHashMap<>();
    private final Map<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();
    private final Map<String, String> sessionTitles = new ConcurrentHashMap<>();
    private final SessionPersistence persistence = new SessionPersistence();
    private final boolean cliApproval;

    private record ActiveRun(CompletableFuture<?> future, RunCancellation cancellation) {}

    public AgentSessionService(AgentApplication application) {
        this(application, true);
    }

    public AgentSessionService(AgentApplication application, boolean cliApproval) {
        this.application = application;
        this.cliApproval = cliApproval;
    }

    public AgentSession createSession(Path workspace) {
        AgentSession session = new AgentSession(normalizeWorkspace(workspace), application);
        sessions.put(session.sessionId(), session);
        initSessionFiles(session);
        return session;
    }

    public AgentSession createSession(String sessionId, Path workspace) {
        AgentSession session = new AgentSession(sessionId, normalizeWorkspace(workspace), application);
        sessions.put(sessionId, session);
        initSessionFiles(session);
        return session;
    }

    public AgentSession restoreSession(SessionPersistence.StoredSession stored) {
        Path workspace = normalizeWorkspace(Path.of(stored.workspace()));
        AgentSession session = new AgentSession(stored.sessionId(), workspace, application);
        restoreHistory(session.agentHistory(), stored.agentMessages());
        restoreHistory(session.chatHistory(), stored.chatMessages());
        restoreTurnsAndSummaries(session, workspace, stored.sessionId(), stored);
        sessions.put(session.sessionId(), session);
        sessionTitles.put(session.sessionId(), stored.title());
        return session;
    }

    private void restoreTurnsAndSummaries(
            AgentSession session,
            Path workspace,
            String sessionId,
            SessionPersistence.StoredSession stored
    ) {
        try {
            SessionPersistence.StoredSessionHistory history = persistence.loadHistory(workspace, sessionId);
            session.restoreTurns(history.agentTurns(), history.chatTurns());
            SessionPersistence.StoredSessionSummary summary = persistence.loadSummary(workspace, sessionId);
            session.setAgentCompressedSummary(summary.effectiveAgentCompressedSummary());
            session.setChatCompressedSummary(summary.effectiveChatCompressedSummary());
        } catch (Exception ignored) {
            // fall through to migration from messages
        }
        migrateTurnsIfEmpty(session, stored);
    }

    private static void migrateTurnsIfEmpty(AgentSession session, SessionPersistence.StoredSession stored) {
        if (session.agentTurns().isEmpty() && stored.agentMessages() != null && !stored.agentMessages().isEmpty()) {
            MessageHistory rebuilt = new MessageHistory();
            restoreHistory(rebuilt, stored.agentMessages());
            session.restoreTurns(HistoryTranscriptBuilder.buildTurns(rebuilt), session.chatTurns());
        }
        if (session.chatTurns().isEmpty() && stored.chatMessages() != null && !stored.chatMessages().isEmpty()) {
            MessageHistory rebuilt = new MessageHistory();
            restoreHistory(rebuilt, stored.chatMessages());
            session.restoreTurns(session.agentTurns(), HistoryTranscriptBuilder.buildTurns(rebuilt));
        }
    }

    public AgentSession importSession(
            String sessionId,
            Path workspace,
            MessageHistory agentHistory,
            MessageHistory chatHistory,
            String title
    ) {
        AgentSession session = new AgentSession(sessionId, workspace, application);
        copyHistory(session.agentHistory(), agentHistory);
        copyHistory(session.chatHistory(), chatHistory);
        session.restoreTurns(
                HistoryTranscriptBuilder.buildTurns(session.agentHistory()),
                HistoryTranscriptBuilder.buildTurns(session.chatHistory())
        );
        sessions.put(sessionId, session);
        if (title != null && !title.isBlank()) {
            sessionTitles.put(sessionId, title.strip());
        }
        return session;
    }

    public AgentSession createSessionFromTranscript(
            String sessionId,
            Path workspace,
            ConversationTranscript transcript,
            String title
    ) {
        AgentSession session = createSession(sessionId, workspace);
        for (com.aicode.app.ui.ChatTurn turn : transcript.turns()) {
            if (turn.userText() != null && !turn.userText().isBlank()) {
                session.agentHistory().addUser(turn.userText(), turn.userText());
            }
            if (!turn.assistantText().isEmpty()) {
                session.agentHistory().addAssistant(turn.assistantText());
            }
            session.appendAgentTurn(ChatTurnDto.userTurn(
                    turn.userText(),
                    turn.activities(),
                    turn.assistantText(),
                    turn.createdAt()
            ));
        }
        if (title != null && !title.isBlank()) {
            sessionTitles.put(sessionId, title.strip());
        }
        return session;
    }

    public AgentSession migrateSession(
            String sessionId,
            Path workspace,
            AgentSessionService previousService,
            ConversationTranscript transcriptFallback,
            String title
    ) {
        if (previousService != null) {
            try {
                AgentSession previous = previousService.getSession(sessionId);
                boolean previousHasHistory = !previous.agentHistory().getMessages().isEmpty()
                        || !previous.chatHistory().getMessages().isEmpty();
                boolean transcriptHasTurns = transcriptFallback != null
                        && !transcriptFallback.turns().isEmpty();
                if (previousHasHistory || !transcriptHasTurns) {
                    return importSession(
                            sessionId,
                            workspace,
                            previous.agentHistory(),
                            previous.chatHistory(),
                            title
                    );
                }
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        for (SessionPersistence.StoredSession stored : loadStoredSessions(workspace)) {
            if (stored.sessionId().equals(sessionId)) {
                return restoreSession(stored);
            }
        }
        if (transcriptFallback != null && !transcriptFallback.turns().isEmpty()) {
            return createSessionFromTranscript(sessionId, workspace, transcriptFallback, title);
        }
        return createSession(sessionId, workspace);
    }

    public List<SessionPersistence.StoredSession> loadStoredSessions(Path workspace) {
        try {
            return persistence.loadAll(workspace);
        } catch (Exception e) {
            return List.of();
        }
    }

    public void setSessionTitle(String sessionId, String title) {
        if (title != null && !title.isBlank()) {
            sessionTitles.put(sessionId, title.strip());
            persistQuietly(sessionId);
        }
    }

    /** Flush all in-memory sessions to disk (e.g. before closing the window). */
    public void flushAllSessions() {
        for (String sessionId : List.copyOf(sessions.keySet())) {
            persistQuietly(sessionId);
        }
    }

    public AgentSession getSession(String sessionId) {
        AgentSession session = sessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Unknown session: " + sessionId);
        }
        return session;
    }

    public AgentSession getOrCreateDefaultSession() {
        if (sessions.isEmpty()) {
            return createSession(application.config().workspace());
        }
        return sessions.values().iterator().next();
    }

    public CompletableFuture<Agent.AgentResult> sendMessage(
            String sessionId,
            String userMessage,
            AgentEventListener listener
    ) {
        return sendAgentMessage(sessionId, userMessage, userMessage, listener);
    }

    public CompletableFuture<Agent.AgentResult> sendAgentMessage(
            String sessionId,
            String userMessage,
            AgentEventListener listener
    ) {
        return sendAgentMessage(sessionId, userMessage, userMessage, listener, null);
    }

    public CompletableFuture<Agent.AgentResult> sendAgentMessage(
            String sessionId,
            String userMessage,
            String displayText,
            AgentEventListener listener
    ) {
        return sendAgentMessage(sessionId, userMessage, displayText, listener, null);
    }

    public CompletableFuture<Agent.AgentResult> sendAgentMessage(
            String sessionId,
            String userMessage,
            String displayText,
            AgentEventListener listener,
            TurnContext turnContext
    ) {
        AgentSession session = getSession(sessionId);
        TurnContext ctx = turnContext != null
                ? turnContext
                : TurnContext.of(session.workspace(), null);
        session.setPendingTurnContext(ctx);
        MessageHistory history = session.agentHistory();
        int turnsBefore = session.agentTurns().size();
        history.addUser(userMessage, displayText);
        persistQuietly(sessionId);

        ApprovalGate gate = new ApprovalGate(
                listener,
                cliApproval ? CliApprovalHandler::prompt : null
        );
        FileEditGate fileEditGate = new FileEditGate();
        approvalGates.put(sessionId, gate);
        fileEditGates.put(sessionId, fileEditGate);
        RunCancellation cancellation = new RunCancellation();
        Agent.ToolExecutor baseExecutor = application.createToolExecutor(gate, fileEditGate, listener);
        Agent.ToolExecutor executor = (name, input) -> {
            if (cancellation.isCancelled()) {
                return CompletableFuture.failedFuture(new RunCancelledException(cancellation.partialText()));
            }
            return baseExecutor.execute(name, input);
        };
        Agent.AgentConfig baseConfig = application.toAgentConfig(executor);
        Agent.AgentConfig config = new Agent.AgentConfig(
                baseConfig.provider(),
                baseConfig.system(),
                baseConfig.tools(),
                baseConfig.executeTool(),
                baseConfig.maxIterations(),
                baseConfig.outputLimits(),
                baseConfig.parallelToolCalls(),
                cancellation
        );

        CompletableFuture<Agent.AgentResult> future = prepareMessages(session, ChatMode.AGENT)
                .thenCompose(messages -> Agent.runAgentStream(config, messages, listener)
                        .thenApply(result -> {
                            appendAgentTurn(history, result);
                            return result;
                        }))
                .whenComplete((result, error) -> {
                    recordCompletedTurn(session, ChatMode.AGENT, turnsBefore);
                    activeRuns.remove(sessionId);
                    persistQuietly(sessionId);
                });
        activeRuns.put(sessionId, new ActiveRun(future, cancellation));
        return future;
    }

    /** Plain chat mode: direct LLM stream, no tools. */
    public CompletableFuture<String> sendChatMessage(
            String sessionId,
            String userMessage,
            AgentEventListener listener
    ) {
        return sendChatMessage(sessionId, userMessage, userMessage, listener);
    }

    public CompletableFuture<String> sendChatMessage(
            String sessionId,
            String userMessage,
            String displayText,
            AgentEventListener listener
    ) {
        return sendChatMessage(sessionId, userMessage, displayText, listener, null);
    }

    public CompletableFuture<String> sendChatMessage(
            String sessionId,
            String userMessage,
            String displayText,
            AgentEventListener listener,
            TurnContext turnContext
    ) {
        AgentSession session = getSession(sessionId);
        TurnContext ctx = turnContext != null
                ? turnContext
                : TurnContext.of(session.workspace(), null);
        session.setPendingTurnContext(ctx);
        MessageHistory history = session.chatHistory();
        int turnsBefore = session.chatTurns().size();
        history.addUser(userMessage, displayText);
        persistQuietly(sessionId);

        AgentEventListener events = listener != null ? listener : AgentEventListener.NOOP;
        RunCancellation cancellation = new RunCancellation();

        CompletableFuture<String> future = prepareMessages(session, ChatMode.CHAT)
                .thenCompose(messages -> CompletableFuture.supplyAsync(() -> {
                    try {
                        StreamingChatHelper.StreamResult result = StreamingChatHelper.streamWithRetry(
                                application.provider(),
                                messages,
                                application.chatOptions(),
                                application.config().outputTokenLimits(),
                                events,
                                null,
                                cancellation
                        );
                        events.onEvent(new AgentEvent.Done(1, 0, 0));
                        return result.text();
                    } catch (RunCancelledException e) {
                        events.onEvent(new AgentEvent.Cancelled(e.partialText()));
                        events.onEvent(new AgentEvent.Done(1, 0, 0));
                        return e.partialText();
                    }
                }))
                .thenApply(text -> {
                    if (!text.isBlank()) {
                        history.addAssistant(text);
                    }
                    return text;
                })
                .whenComplete((result, error) -> {
                    recordCompletedTurn(session, ChatMode.CHAT, turnsBefore);
                    activeRuns.remove(sessionId);
                    persistQuietly(sessionId);
                });
        activeRuns.put(sessionId, new ActiveRun(future, cancellation));
        return future;
    }

    private CompletableFuture<List<Message>> prepareMessages(AgentSession session, ChatMode mode) {
        TurnContext turnContext = session.consumePendingTurnContext();
        MessageHistory history = mode == ChatMode.CHAT ? session.chatHistory() : session.agentHistory();
        List<Message> all = history.getMessages();
        int window = application.config().contextWindow();
        int reserved = reservedContextTokens(mode);
        int messageBudget = Math.max(1024, window - reserved);
        HistoryCompactor.CompactResult compacted = HistoryCompactor.compact(all, messageBudget);
        if (compacted.changed()) {
            history.replaceAll(compacted.messages());
            all = compacted.messages();
        }
        List<Message> repaired = ToolMessageRepair.repair(all);
        if (ToolMessageRepair.differs(all, repaired)) {
            history.replaceAll(repaired);
        }
        all = repaired;
        int compressThreshold = (int) (messageBudget * 0.65);

        if (firstMessageIsSummary(all) && !Compressor.needsCompression(all, compressThreshold)) {
            return CompletableFuture.completedFuture(
                    selectForModel(all, messageBudget, turnContext)
            );
        }

        if (!Compressor.needsCompression(all, compressThreshold)) {
            return CompletableFuture.completedFuture(
                    selectForModel(all, messageBudget, turnContext)
            );
        }

        return Compressor.compressConversation(
                new Compressor.CompressorConfig(
                        application.provider(),
                        compressThreshold,
                        6,
                        1024
                ),
                all
        ).thenApply(result -> {
            if (result.compressed()) {
                history.replaceAll(result.messages());
                String summary = extractSummaryText(result.messages());
                if (summary != null) {
                    if (mode == ChatMode.CHAT) {
                        session.setChatCompressedSummary(summary);
                        session.appendChatTurn(ChatTurnDto.standaloneNotice(
                                "[Previous conversation summary]\n" + summary
                        ));
                    } else {
                        session.setAgentCompressedSummary(summary);
                        session.appendAgentTurn(ChatTurnDto.standaloneNotice(
                                "[Previous conversation summary]\n" + summary
                        ));
                    }
                }
            }
            List<Message> current = ToolMessageRepair.repair(history.getMessages());
            if (ToolMessageRepair.differs(history.getMessages(), current)) {
                history.replaceAll(current);
            }
            return selectForModel(current, messageBudget, turnContext);
        });
    }

    private static List<Message> selectForModel(
            List<Message> messages,
            int messageBudget,
            TurnContext turnContext
    ) {
        return TurnContext.injectOnLastUserMessage(
                ToolMessageRepair.repair(Context.selectMessages(messages, messageBudget)),
                turnContext
        );
    }

    private static boolean firstMessageIsSummary(List<Message> messages) {
        if (messages.isEmpty()) {
            return false;
        }
        Message first = messages.getFirst();
        return first.isStringContent()
                && first.contentText().startsWith("[Previous conversation summary]");
    }

    private static String extractSummaryText(List<Message> messages) {
        if (!firstMessageIsSummary(messages)) {
            return null;
        }
        return messages.getFirst().contentText()
                .replaceFirst("\\[Previous conversation summary]\\n?", "")
                .strip();
    }

    private void recordCompletedTurn(AgentSession session, ChatMode mode, int turnsBefore) {
        MessageHistory history = mode == ChatMode.CHAT ? session.chatHistory() : session.agentHistory();
        List<ChatTurnDto> turns = session.turnsForMode(mode);
        for (int i = turnsBefore; i < turns.size(); i++) {
            if (!turns.get(i).standalone()) {
                return;
            }
        }
        ChatTurnDto lastTurn = HistoryTranscriptBuilder.buildLastTurn(history);
        if (lastTurn == null || lastTurn.standalone()) {
            return;
        }
        session.appendTurn(mode, withTimestamp(lastTurn));
    }

    private static ChatTurnDto withTimestamp(ChatTurnDto turn) {
        if (turn.createdAt() != null && !turn.createdAt().isBlank()) {
            return turn;
        }
        return turn.withCreatedAt(Instant.now().toString());
    }

    private int reservedContextTokens(ChatMode mode) {
        if (mode == ChatMode.CHAT) {
            return com.aicode.agent.TokenCounter.estimateConversationTokens(
                    List.of(),
                    application.chatSystemPrompt(),
                    List.of()
            );
        }
        return com.aicode.agent.TokenCounter.estimateConversationTokens(
                List.of(),
                application.systemPrompt(),
                application.tools()
        );
    }

    private static void appendAgentTurn(MessageHistory history, Agent.AgentResult result) {
        for (Message message : result.appendedMessages()) {
            history.addMessage(message);
        }
        if (result.text() != null && !result.text().isBlank()) {
            history.addAssistant(result.text());
        }
    }

    private void persistQuietly(String sessionId) {
        try {
            AgentSession session = sessions.get(sessionId);
            if (session == null || !persistence.exists(session.workspace(), sessionId)) {
                return;
            }
            String title = sessionTitles.getOrDefault(sessionId, "对话");
            List<SessionPersistence.StoredMessage> agent = toStored(session.agentHistory());
            List<SessionPersistence.StoredMessage> chat = toStored(session.chatHistory());
            List<ChatTurnDto> agentTurns = session.agentTurns();
            List<ChatTurnDto> chatTurns = session.chatTurns();
            persistence.saveSplit(
                    new SessionPersistence.StoredSessionSummary(
                            sessionId,
                            title,
                            session.workspace().toString(),
                            java.time.Instant.now().toString(),
                            session.agentCompressedSummary(),
                            session.agentCompressedSummary(),
                            session.chatCompressedSummary(),
                            agentTurns.size(),
                            chatTurns.size()
                    ),
                    new SessionPersistence.StoredSessionHistory(
                            agent,
                            chat,
                            agentTurns,
                            chatTurns
                    )
            );
        } catch (Exception e) {
            System.err.println("[SessionPersistence] Failed to save session " + sessionId + ": " + e.getMessage());
        }
    }

    private void initSessionFiles(AgentSession session) {
        try {
            String title = sessionTitles.getOrDefault(session.sessionId(), "对话");
            if (!persistence.exists(session.workspace(), session.sessionId())) {
                persistence.createEmpty(session.workspace(), session.sessionId(), title);
            }
        } catch (Exception e) {
            System.err.println("[SessionPersistence] Failed to init session "
                    + session.sessionId() + ": " + e.getMessage());
        }
    }

    private static Path normalizeWorkspace(Path workspace) {
        return WorkingDirectory.normalizeWorkspace(workspace);
    }

    private static List<SessionPersistence.StoredMessage> toStored(MessageHistory history) {
        List<SessionPersistence.StoredMessage> stored = new ArrayList<>();
        List<Message> messages = history.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            String display = "user".equals(messages.get(i).role()) ? history.userDisplayText(i) : null;
            stored.add(SessionMessageCodec.toStored(messages.get(i), display));
        }
        return stored;
    }

    private static void restoreHistory(MessageHistory history, List<SessionPersistence.StoredMessage> messages) {
        if (messages == null) {
            return;
        }
        for (SessionPersistence.StoredMessage stored : messages) {
            Message message = SessionMessageCodec.fromStored(stored);
            if ("user".equals(stored.role()) && message.isStringContent()) {
                history.addUser(message.contentText(), SessionMessageCodec.displayContent(stored));
            } else {
                history.addMessage(message);
            }
        }
    }

    private static void copyHistory(MessageHistory target, MessageHistory source) {
        List<Message> messages = source.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if ("user".equals(message.role()) && message.isStringContent()) {
                target.addUser(message.contentText(), source.userDisplayText(i));
            } else {
                target.addMessage(message);
            }
        }
    }

    /** Stops the in-flight agent or chat request for this session. */
    public void cancelSession(String sessionId) {
        ActiveRun run = activeRuns.get(sessionId);
        if (run != null) {
            run.cancellation().cancel();
            run.future().cancel(true);
        }
        ApprovalGate gate = approvalGates.get(sessionId);
        if (gate != null) {
            gate.cancelAll();
        }
        FileEditGate fileEditGate = fileEditGates.get(sessionId);
        if (fileEditGate != null) {
            fileEditGate.cancelAll();
        }
    }

    public boolean isRunning(String sessionId) {
        return activeRuns.containsKey(sessionId);
    }

    public void resolveApproval(String sessionId, String approvalId, boolean approved) {
        getSession(sessionId);
        ApprovalGate gate = approvalGates.get(sessionId);
        if (gate != null) {
            gate.resolve(approvalId, approved);
        }
    }

    public void resolveFileEdit(String sessionId, String editId, boolean kept) {
        getSession(sessionId);
        FileEditGate gate = fileEditGates.get(sessionId);
        if (gate != null) {
            gate.resolve(editId, kept);
        }
    }

    public CompletableFuture<String> sendMessageForCli(String userMessage, AgentEventListener listener) {
        AgentSession session = getOrCreateDefaultSession();
        return sendMessage(session.sessionId(), userMessage, listener)
                .thenApply(result -> Markdown.renderMarkdown(result.text()));
    }

    public List<ChatMessageDto> getHistory(String sessionId) {
        return getHistory(sessionId, ChatMode.AGENT);
    }

    public List<ChatMessageDto> getHistory(String sessionId, ChatMode mode) {
        AgentSession session = getSession(sessionId);
        MessageHistory history = mode == ChatMode.CHAT
                ? session.chatHistory()
                : session.agentHistory();
        List<ChatMessageDto> dtos = new ArrayList<>();
        List<Message> messages = history.getMessages();
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            String content = formatMessage(message);
            if ("user".equals(message.role())) {
                content = history.userDisplayText(i);
            }
            dtos.add(new ChatMessageDto(message.role(), content));
        }
        return dtos;
    }

    public List<ChatTurnDto> getTranscriptTurns(String sessionId) {
        return getTranscriptTurns(sessionId, ChatMode.AGENT);
    }

    public List<ChatTurnDto> getTranscriptTurns(String sessionId, ChatMode mode) {
        AgentSession session = getSession(sessionId);
        List<ChatTurnDto> turns = session.turnsForMode(mode);
        if (!turns.isEmpty()) {
            return turns;
        }
        MessageHistory history = mode == ChatMode.CHAT
                ? session.chatHistory()
                : session.agentHistory();
        return HistoryTranscriptBuilder.buildTurns(history);
    }

    public SessionPersistence.HistoryPage loadTranscriptPage(
            Path workspace,
            String sessionId,
            ChatMode mode,
            int limit,
            int beforeTurnIndex
    ) {
        Path normalized = normalizeWorkspace(workspace);
        try {
            SessionPersistence.HistoryPage page = persistence.loadTurnPage(
                    normalized, sessionId, mode, limit, beforeTurnIndex);
            if (!page.turns().isEmpty() || page.totalTurns() > 0) {
                return page;
            }
        } catch (Exception ignored) {
            // fall through to in-memory / message fallback
        }
        return buildTranscriptPageFromStored(normalized, sessionId, mode, limit, beforeTurnIndex);
    }

    private SessionPersistence.HistoryPage buildTranscriptPageFromStored(
            Path workspace,
            String sessionId,
            ChatMode mode,
            int limit,
            int beforeTurnIndex
    ) {
        try {
            List<ChatTurnDto> allTurns;
            AgentSession session = sessions.get(sessionId);
            if (session != null) {
                allTurns = getTranscriptTurns(sessionId, mode);
            } else {
                SessionPersistence.StoredSessionHistory history = persistence.loadHistory(workspace, sessionId);
                allTurns = mode == ChatMode.CHAT ? history.chatTurns() : history.agentTurns();
                if (allTurns.isEmpty()) {
                    MessageHistory messageHistory = new MessageHistory();
                    restoreHistory(
                            messageHistory,
                            mode == ChatMode.CHAT ? history.chatMessages() : history.agentMessages()
                    );
                    allTurns = HistoryTranscriptBuilder.buildTurns(messageHistory);
                }
            }
            int total = allTurns.size();
            int end = beforeTurnIndex < 0 || beforeTurnIndex > total ? total : beforeTurnIndex;
            int start = Math.max(0, end - Math.max(1, limit));
            return new SessionPersistence.HistoryPage(allTurns.subList(start, end), total, start, start > 0);
        } catch (Exception e) {
            return new SessionPersistence.HistoryPage(List.of(), 0, 0, false);
        }
    }

    public void resetSession(String sessionId) {
        AgentSession session = getSession(sessionId);
        session.agentHistory().clear();
        session.chatHistory().clear();
        session.clearTurns();
        session.clearCompressedSummaries();
        application.taskManager().clear();
        application.scratchpad().clear();
        persistQuietly(sessionId);
    }

    public void closeSession(String sessionId) {
        cancelSession(sessionId);
        AgentSession session = sessions.remove(sessionId);
        approvalGates.remove(sessionId);
        fileEditGates.remove(sessionId);
        activeRuns.remove(sessionId);
        sessionTitles.remove(sessionId);
        if (session != null) {
            try {
                persistence.delete(session.workspace(), sessionId);
            } catch (Exception ignored) {
                // best-effort
            }
        }
    }

    private static String formatMessage(Message message) {
        if (message.isStringContent()) {
            return message.contentText();
        }
        StringBuilder sb = new StringBuilder();
        for (var block : message.contentBlocks()) {
            if (block instanceof com.aicode.agent.llm.TextBlock tb) {
                sb.append(tb.text());
            } else if (block instanceof com.aicode.agent.llm.ToolUseBlock tub) {
                sb.append("[tool:").append(tub.name()).append("]");
            } else {
                sb.append("[tool-result]");
            }
        }
        return sb.toString();
    }
}

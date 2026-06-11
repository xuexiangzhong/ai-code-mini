package com.aicode.app.session;

import com.aicode.agent.Agent;
import com.aicode.agent.Context;
import com.aicode.agent.Markdown;
import com.aicode.agent.MessageHistory;
import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.StreamingChatHelper;
import com.aicode.app.application.AgentApplication;
import com.aicode.app.approval.ApprovalGate;
import com.aicode.app.approval.CliApprovalHandler;
import com.aicode.app.event.AgentEvent;
import com.aicode.app.event.AgentEventListener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class AgentSessionService {
    private final AgentApplication application;
    private final Map<String, AgentSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ApprovalGate> approvalGates = new ConcurrentHashMap<>();
    private final Map<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();
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
        AgentSession session = new AgentSession(workspace, application);
        sessions.put(session.sessionId(), session);
        return session;
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
        return sendAgentMessage(sessionId, userMessage, listener);
    }

    /** Programming agent mode: tools, sandbox, approval, multi-iteration loop. */
    public CompletableFuture<Agent.AgentResult> sendAgentMessage(
            String sessionId,
            String userMessage,
            AgentEventListener listener
    ) {
        AgentSession session = getSession(sessionId);
        MessageHistory history = session.agentHistory();
        history.addUser(userMessage);

        List<Message> messages = Context.selectMessages(
                history.getMessages(),
                application.config().contextWindow()
        );

        ApprovalGate gate = new ApprovalGate(
                listener,
                cliApproval ? CliApprovalHandler::prompt : null
        );
        approvalGates.put(sessionId, gate);
        RunCancellation cancellation = new RunCancellation();
        Agent.ToolExecutor baseExecutor = application.createToolExecutor(gate, listener);
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

        CompletableFuture<Agent.AgentResult> future = Agent.runAgentStream(config, messages, listener)
                .thenApply(result -> {
                    if (result.text() != null && !result.text().isBlank()) {
                        history.addAssistant(result.text());
                    }
                    return result;
                })
                .whenComplete((result, error) -> activeRuns.remove(sessionId));
        activeRuns.put(sessionId, new ActiveRun(future, cancellation));
        return future;
    }

    /** Plain chat mode: direct LLM stream, no tools. */
    public CompletableFuture<String> sendChatMessage(
            String sessionId,
            String userMessage,
            AgentEventListener listener
    ) {
        AgentSession session = getSession(sessionId);
        MessageHistory history = session.chatHistory();
        history.addUser(userMessage);

        List<Message> messages = Context.selectMessages(
                history.getMessages(),
                application.config().contextWindow()
        );
        AgentEventListener events = listener != null ? listener : AgentEventListener.NOOP;
        RunCancellation cancellation = new RunCancellation();

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
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
        }).thenApply(text -> {
            if (!text.isBlank()) {
                history.addAssistant(text);
            }
            return text;
        }).whenComplete((result, error) -> activeRuns.remove(sessionId));
        activeRuns.put(sessionId, new ActiveRun(future, cancellation));
        return future;
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
        for (Message message : history.getMessages()) {
            dtos.add(new ChatMessageDto(message.role(), formatMessage(message)));
        }
        return dtos;
    }

    public void resetSession(String sessionId) {
        AgentSession session = getSession(sessionId);
        session.agentHistory().clear();
        session.chatHistory().clear();
        application.taskManager().clear();
        application.scratchpad().clear();
    }

    public void closeSession(String sessionId) {
        cancelSession(sessionId);
        sessions.remove(sessionId);
        approvalGates.remove(sessionId);
        activeRuns.remove(sessionId);
    }

    private static String formatMessage(Message message) {
        if (message.isStringContent()) {
            return message.contentText();
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : message.contentBlocks()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.text());
            } else {
                sb.append("[tool]");
            }
        }
        return sb.toString();
    }
}

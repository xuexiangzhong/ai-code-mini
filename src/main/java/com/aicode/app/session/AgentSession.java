package com.aicode.app.session;

import com.aicode.agent.MessageHistory;
import com.aicode.agent.TurnContext;
import com.aicode.app.application.AgentApplication;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AgentSession {
    private final String sessionId;
    private final Path workspace;
    private final MessageHistory agentHistory;
    private final MessageHistory chatHistory;
    private final AgentApplication application;
    private final List<ChatTurnDto> agentTurns = new ArrayList<>();
    private final List<ChatTurnDto> chatTurns = new ArrayList<>();
    private String agentCompressedSummary;
    private String chatCompressedSummary;
    private volatile TurnContext pendingTurnContext;

    public AgentSession(Path workspace, AgentApplication application) {
        this(UUID.randomUUID().toString(), workspace, application);
    }

    public AgentSession(String sessionId, Path workspace, AgentApplication application) {
        this.sessionId = sessionId;
        this.workspace = workspace;
        this.agentHistory = new MessageHistory();
        this.chatHistory = new MessageHistory();
        this.application = application;
    }

    public String sessionId() {
        return sessionId;
    }

    public Path workspace() {
        return workspace;
    }

    /** Agent mode conversation history (tools, coding tasks). */
    public MessageHistory history() {
        return agentHistory;
    }

    public MessageHistory agentHistory() {
        return agentHistory;
    }

    /** Plain chat mode history (no tools). */
    public MessageHistory chatHistory() {
        return chatHistory;
    }

    public AgentApplication application() {
        return application;
    }

    /** Append-only UI transcript for agent mode. */
    public List<ChatTurnDto> agentTurns() {
        return List.copyOf(agentTurns);
    }

    /** Append-only UI transcript for chat mode. */
    public List<ChatTurnDto> chatTurns() {
        return List.copyOf(chatTurns);
    }

    public void appendAgentTurn(ChatTurnDto turn) {
        if (turn != null) {
            agentTurns.add(turn);
        }
    }

    public void appendChatTurn(ChatTurnDto turn) {
        if (turn != null) {
            chatTurns.add(turn);
        }
    }

    public void restoreTurns(List<ChatTurnDto> agent, List<ChatTurnDto> chat) {
        agentTurns.clear();
        chatTurns.clear();
        if (agent != null) {
            agentTurns.addAll(agent);
        }
        if (chat != null) {
            chatTurns.addAll(chat);
        }
    }

    public void clearTurns() {
        agentTurns.clear();
        chatTurns.clear();
    }

    public List<ChatTurnDto> turnsForMode(ChatMode mode) {
        return mode == ChatMode.CHAT ? chatTurns() : agentTurns();
    }

    public void appendTurn(ChatMode mode, ChatTurnDto turn) {
        if (mode == ChatMode.CHAT) {
            appendChatTurn(turn);
        } else {
            appendAgentTurn(turn);
        }
    }

    public String agentCompressedSummary() {
        return agentCompressedSummary;
    }

    public String chatCompressedSummary() {
        return chatCompressedSummary;
    }

    public void setAgentCompressedSummary(String agentCompressedSummary) {
        this.agentCompressedSummary = agentCompressedSummary;
    }

    public void setChatCompressedSummary(String chatCompressedSummary) {
        this.chatCompressedSummary = chatCompressedSummary;
    }

    public void clearCompressedSummaries() {
        agentCompressedSummary = null;
        chatCompressedSummary = null;
    }

    public void setPendingTurnContext(TurnContext turnContext) {
        this.pendingTurnContext = turnContext;
    }

    public TurnContext consumePendingTurnContext() {
        TurnContext ctx = pendingTurnContext;
        pendingTurnContext = null;
        return ctx;
    }
}

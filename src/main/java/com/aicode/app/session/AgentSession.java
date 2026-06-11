package com.aicode.app.session;

import com.aicode.agent.MessageHistory;
import com.aicode.app.application.AgentApplication;

import java.nio.file.Path;
import java.util.UUID;

public final class AgentSession {
    private final String sessionId;
    private final Path workspace;
    private final MessageHistory agentHistory;
    private final MessageHistory chatHistory;
    private final AgentApplication application;

    public AgentSession(Path workspace, AgentApplication application) {
        this.sessionId = UUID.randomUUID().toString();
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
}

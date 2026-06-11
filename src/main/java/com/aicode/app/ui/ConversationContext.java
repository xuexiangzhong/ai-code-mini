package com.aicode.app.ui;

/** Per-conversation UI state: backend session id, title, and rendered transcript. */
public final class ConversationContext {
    private String sessionId;
    private String title;
    private final ConversationTranscript transcript = new ConversationTranscript();
    private UiAgentBridge bridge;
    private volatile boolean generating;

    public ConversationContext(String sessionId, String title) {
        this.sessionId = sessionId;
        this.title = title;
    }

    public String sessionId() {
        return sessionId;
    }

    public String title() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ConversationTranscript transcript() {
        return transcript;
    }

    public UiAgentBridge bridge() {
        return bridge;
    }

    public void setBridge(UiAgentBridge bridge) {
        this.bridge = bridge;
    }

    public void rebindSession(String sessionId, UiAgentBridge bridge) {
        this.sessionId = sessionId;
        this.bridge = bridge;
    }

    public boolean generating() {
        return generating;
    }

    public void setGenerating(boolean generating) {
        this.generating = generating;
    }
}

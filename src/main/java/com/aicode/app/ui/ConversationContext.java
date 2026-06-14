package com.aicode.app.ui;

/** Per-conversation UI state: backend session id, title, and rendered transcript. */
public final class ConversationContext {
    private String sessionId;
    private String title;
    private final ConversationTranscript transcript = new ConversationTranscript();
    private UiAgentBridge bridge;
    private volatile boolean generating;
    private int oldestLoadedTurnIndex;
    private int totalTurns;
    private volatile boolean loadingOlderTurns;

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

    public int oldestLoadedTurnIndex() {
        return oldestLoadedTurnIndex;
    }

    public int totalTurns() {
        return totalTurns;
    }

    public boolean hasOlderTurns() {
        return oldestLoadedTurnIndex > 0;
    }

    public boolean loadingOlderTurns() {
        return loadingOlderTurns;
    }

    public void setLoadingOlderTurns(boolean loadingOlderTurns) {
        this.loadingOlderTurns = loadingOlderTurns;
    }

    public void setTranscriptPagination(int oldestLoadedTurnIndex, int totalTurns) {
        this.oldestLoadedTurnIndex = oldestLoadedTurnIndex;
        this.totalTurns = totalTurns;
    }
}

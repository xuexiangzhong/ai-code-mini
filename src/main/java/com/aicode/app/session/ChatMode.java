package com.aicode.app.session;

public enum ChatMode {
    CHAT("问答模式"),
    AGENT("编程 Agent");

    private final String label;

    ChatMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}

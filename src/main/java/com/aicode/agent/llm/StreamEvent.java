package com.aicode.agent.llm;

public record StreamEvent(String type, String text, ChatResponse response) {
    public StreamEvent(String type) {
        this(type, null, null);
    }

    public StreamEvent(String type, String text) {
        this(type, text, null);
    }
}

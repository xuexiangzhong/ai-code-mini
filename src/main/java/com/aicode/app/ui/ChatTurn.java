package com.aicode.app.ui;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One user message exchange: optional user text, activity log, and assistant reply. */
public final class ChatTurn {
    private final String userText;
    private final String createdAt;
    private final List<String> activities = new ArrayList<>();
    private final StringBuilder assistant = new StringBuilder();

    public ChatTurn(String userText) {
        this(userText, Instant.now().toString());
    }

    public ChatTurn(String userText, String createdAt) {
        this.userText = userText;
        this.createdAt = createdAt;
    }

    public String userText() {
        return userText;
    }

    public String createdAt() {
        return createdAt;
    }

    public List<String> activities() {
        return Collections.unmodifiableList(activities);
    }

    void addActivity(String text) {
        activities.add(text);
    }

    public String assistantText() {
        return assistant.toString();
    }

    void appendAssistant(String text) {
        assistant.append(text);
    }

    String latestActivity() {
        return activities.isEmpty() ? "" : activities.getLast();
    }
}

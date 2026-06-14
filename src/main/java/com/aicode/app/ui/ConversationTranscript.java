package com.aicode.app.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Structured conversation transcript backed by chat turns. */
public final class ConversationTranscript {
    private final List<ChatTurn> turns = new ArrayList<>();
    private ChatTurn openTurn;

    public List<ChatTurn> turns() {
        return Collections.unmodifiableList(turns);
    }

    public ChatTurn openTurn() {
        return openTurn;
    }

    public void clear() {
        turns.clear();
        openTurn = null;
    }

    public void startTurn(String userText) {
        startTurn(userText, java.time.Instant.now().toString());
    }

    public void startTurn(String userText, String createdAt) {
        openTurn = new ChatTurn(userText, createdAt);
        turns.add(openTurn);
    }

    public void addStandaloneNotice(String text) {
        addStandaloneNotice(text, java.time.Instant.now().toString());
    }

    public void addStandaloneNotice(String text, String createdAt) {
        if (text == null || text.isBlank()) {
            return;
        }
        ChatTurn notice = new ChatTurn(null, createdAt);
        notice.addActivity(text.strip());
        turns.add(notice);
        openTurn = null;
    }

    public void prependStandaloneNotice(String text) {
        prependStandaloneNotice(text, null);
    }

    public void prependStandaloneNotice(String text, String createdAt) {
        if (text == null || text.isBlank()) {
            return;
        }
        ChatTurn notice = new ChatTurn(null, createdAt);
        notice.addActivity(text.strip());
        turns.addFirst(notice);
        openTurn = null;
    }

    public void prependTurn(String userText, List<String> activities, String assistantText) {
        prependTurn(userText, activities, assistantText, null);
    }

    public void prependTurn(String userText, List<String> activities, String assistantText, String createdAt) {
        ChatTurn turn = new ChatTurn(userText, createdAt);
        if (activities != null) {
            for (String activity : activities) {
                turn.addActivity(activity);
            }
        }
        if (assistantText != null && !assistantText.isBlank()) {
            turn.appendAssistant(assistantText);
        }
        turns.addFirst(turn);
        openTurn = null;
    }

    public void appendAssistant(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ensureOpenTurn();
        openTurn.appendAssistant(text);
    }

    public void addActivity(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        ensureOpenTurn();
        openTurn.addActivity(text.strip());
    }

    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        for (ChatTurn turn : turns) {
            if (turn.userText() != null) {
                sb.append("You: ").append(turn.userText()).append('\n');
            }
            for (String activity : turn.activities()) {
                sb.append("· ").append(activity).append('\n');
            }
            if (!turn.assistantText().isEmpty()) {
                sb.append("Assistant: ").append(turn.assistantText());
                if (!turn.assistantText().endsWith("\n")) {
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private void ensureOpenTurn() {
        if (openTurn == null) {
            openTurn = new ChatTurn(null);
            turns.add(openTurn);
        }
    }
}

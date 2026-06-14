package com.aicode.app.session;

import com.aicode.agent.MessageHistory;
import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.ToolUseBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Build UI transcript turns from persisted agent history. */
public final class HistoryTranscriptBuilder {
    private HistoryTranscriptBuilder() {}

    public static List<ChatTurnDto> buildTurns(MessageHistory history) {
        List<Message> messages = history.getMessages();
        List<ChatTurnDto> turns = new ArrayList<>();
        TurnAccumulator current = null;

        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if ("user".equals(message.role())) {
                if (message.isStringContent()) {
                    String content = message.contentText();
                    if (content.startsWith("[Previous conversation summary]")) {
                        if (current != null) {
                            turns.add(current.build());
                            current = null;
                        }
                        turns.add(ChatTurnDto.standaloneNotice(content));
                        continue;
                    }
                    if (current != null) {
                        turns.add(current.build());
                    }
                    current = new TurnAccumulator(history.userDisplayText(i));
                    continue;
                }
                if (current != null && hasToolResults(message)) {
                    continue;
                }
            } else if ("assistant".equals(message.role()) && current != null) {
                appendAssistant(message, current);
            }
        }
        if (current != null) {
            turns.add(current.build());
        }
        return turns;
    }

    /** Builds only the most recent turn from full history (for append-after-send). */
    public static ChatTurnDto buildLastTurn(MessageHistory history) {
        List<ChatTurnDto> turns = buildTurns(history);
        return turns.isEmpty() ? null : turns.getLast();
    }

    /** Rebuilds turns from messages at/after {@code startMessageIndex} (migration helper). */
    public static List<ChatTurnDto> buildTurnsSince(MessageHistory history, int startMessageIndex) {
        if (startMessageIndex <= 0) {
            return buildTurns(history);
        }
        MessageHistory slice = new MessageHistory();
        List<Message> messages = history.getMessages();
        for (int i = startMessageIndex; i < messages.size(); i++) {
            Message message = messages.get(i);
            if ("user".equals(message.role()) && message.isStringContent()) {
                slice.addUser(message.contentText(), history.userDisplayText(i));
            } else {
                slice.addMessage(message);
            }
        }
        return buildTurns(slice);
    }

    private static boolean hasToolResults(Message message) {
        if (message.isStringContent()) {
            return false;
        }
        for (ContentBlock block : message.contentBlocks()) {
            if (block instanceof ToolUseBlock) {
                return false;
            }
        }
        return true;
    }

    private static void appendAssistant(Message message, TurnAccumulator current) {
        if (message.isStringContent()) {
            current.appendAssistant(message.contentText());
            return;
        }
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : message.contentBlocks()) {
            if (block instanceof TextBlock tb && !tb.text().isBlank()) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(tb.text());
            } else if (block instanceof ToolUseBlock tub) {
                current.addActivity("▶ " + tub.name() + " " + summarizeInput(tub.input()));
            }
        }
        if (!text.isEmpty()) {
            current.appendAssistant(text.toString());
        }
    }

    private static String summarizeInput(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String oneLine = input.toString().replace('\n', ' ');
        return oneLine.length() > 120 ? oneLine.substring(0, 120) + "…" : oneLine;
    }

    private static final class TurnAccumulator {
        private final String userText;
        private final List<String> activities = new ArrayList<>();
        private final StringBuilder assistantText = new StringBuilder();

        TurnAccumulator(String userText) {
            this.userText = userText;
        }

        void addActivity(String activity) {
            activities.add(activity);
        }

        void appendAssistant(String text) {
            if (text == null || text.isBlank()) {
                return;
            }
            if (!assistantText.isEmpty()) {
                assistantText.append('\n');
            }
            assistantText.append(text);
        }

        ChatTurnDto build() {
            return new ChatTurnDto(userText, List.copyOf(activities), assistantText.toString());
        }
    }
}

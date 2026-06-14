package com.aicode.app.session;

import java.time.Instant;
import java.util.List;

/** One rendered chat turn for UI restore. */
public record ChatTurnDto(
        String userText,
        List<String> activities,
        String assistantText,
        boolean standalone,
        String createdAt
) {
    public ChatTurnDto(String userText, List<String> activities, String assistantText) {
        this(userText, activities, assistantText, false, null);
    }

    public static ChatTurnDto userTurn(String userText, List<String> activities, String assistantText) {
        return userTurn(userText, activities, assistantText, Instant.now().toString());
    }

    public static ChatTurnDto userTurn(
            String userText,
            List<String> activities,
            String assistantText,
            String createdAt
    ) {
        return new ChatTurnDto(userText, activities, assistantText, false, createdAt);
    }

    public static ChatTurnDto standaloneNotice(String text) {
        return standaloneNotice(text, Instant.now().toString());
    }

    public static ChatTurnDto standaloneNotice(String text, String createdAt) {
        return new ChatTurnDto(null, List.of(), text, true, createdAt);
    }

    public ChatTurnDto withCreatedAt(String createdAt) {
        return new ChatTurnDto(userText, activities, assistantText, standalone, createdAt);
    }
}

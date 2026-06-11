package com.aicode.agent.llm;

import java.util.List;
import java.util.Map;

public record ChatResponse(
        List<ContentBlock> content,
        String text,
        String stopReason,
        Map<String, Integer> usage
) {
    public ChatResponse {
        if (content == null) {
            content = List.of();
        }
        if (text == null) {
            text = "";
        }
        if (stopReason == null) {
            stopReason = "";
        }
        if (usage == null) {
            usage = Map.of("input_tokens", 0, "output_tokens", 0);
        }
    }

    public int inputTokens() {
        return usage.getOrDefault("input_tokens", 0);
    }

    public int outputTokens() {
        return usage.getOrDefault("output_tokens", 0);
    }
}

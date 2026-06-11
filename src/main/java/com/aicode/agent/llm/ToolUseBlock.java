package com.aicode.agent.llm;

import java.util.Map;

public record ToolUseBlock(String id, String name, Map<String, Object> input) implements ContentBlock {
    public ToolUseBlock {
        if (input == null) {
            input = Map.of();
        }
    }

    public String type() {
        return "tool_use";
    }
}

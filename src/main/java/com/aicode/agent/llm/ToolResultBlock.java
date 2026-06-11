package com.aicode.agent.llm;

public record ToolResultBlock(String toolUseId, String content, boolean isError) implements ContentBlock {
    public ToolResultBlock(String toolUseId, String content) {
        this(toolUseId, content, false);
    }

    public String type() {
        return "tool_result";
    }
}

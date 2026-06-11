package com.aicode.agent.llm;

import java.util.ArrayList;
import java.util.List;

public final class LLMHelpers {
    private LLMHelpers() {}

    public static String extractText(List<ContentBlock> content) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.text());
            }
        }
        return sb.toString();
    }

    public static List<ToolUseBlock> extractToolUses(List<ContentBlock> content) {
        List<ToolUseBlock> uses = new ArrayList<>();
        for (ContentBlock block : content) {
            if (block instanceof ToolUseBlock tub) {
                uses.add(tub);
            }
        }
        return uses;
    }

    public static ToolResultBlock createToolResult(String toolUseId, String content) {
        return createToolResult(toolUseId, content, false);
    }

    public static ToolResultBlock createToolResult(String toolUseId, String content, boolean isError) {
        return new ToolResultBlock(toolUseId, content, isError);
    }
}

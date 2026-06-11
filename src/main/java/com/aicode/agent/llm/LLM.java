package com.aicode.agent.llm;

/**
 * Unified LLM module exports (mirrors {@code python/src/llm/__init__.py} and {@code typescript/src/llm/index.ts}).
 */
public final class LLM {
    private LLM() {}

    // Types are accessed directly; this class provides factory and helper shortcuts.

    public static LLMProvider createProvider(ProviderFactory.ProviderConfig config) {
        return ProviderFactory.createProvider(config);
    }

    public static String extractText(java.util.List<ContentBlock> content) {
        return LLMHelpers.extractText(content);
    }

    public static java.util.List<ToolUseBlock> extractToolUses(java.util.List<ContentBlock> content) {
        return LLMHelpers.extractToolUses(content);
    }

    public static ToolResultBlock createToolResult(String toolUseId, String content) {
        return LLMHelpers.createToolResult(toolUseId, content);
    }

    public static ToolResultBlock createToolResult(String toolUseId, String content, boolean isError) {
        return LLMHelpers.createToolResult(toolUseId, content, isError);
    }
}

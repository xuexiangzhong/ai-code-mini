package com.aicode.agent;

import java.util.concurrent.CompletableFuture;

/** Caps per-call tool output size sent back to the LLM in the current run. */
public final class ToolOutputLimiter {
    public static final int DEFAULT_MAX_CHARS = 8192;

    private ToolOutputLimiter() {}

    public static Agent.ToolExecutor wrap(Agent.ToolExecutor delegate) {
        return wrap(delegate, DEFAULT_MAX_CHARS);
    }

    public static Agent.ToolExecutor wrap(Agent.ToolExecutor delegate, int maxChars) {
        return (name, input) -> delegate.execute(name, input)
                .thenApply(result -> limit(result, maxChars));
    }

    public static String limit(String content) {
        return limit(content, DEFAULT_MAX_CHARS);
    }

    public static String limit(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars)
                + "\n…(tool output truncated, total " + content.length()
                + " chars. Use offset/limit or narrower queries.)";
    }
}

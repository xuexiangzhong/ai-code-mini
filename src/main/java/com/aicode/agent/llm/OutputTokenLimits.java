package com.aicode.agent.llm;

/**
 * Output token budget for LLM requests, with scaling for truncation retries.
 */
public record OutputTokenLimits(int base, int cap, int maxRetries) {
    public static final int DEFAULT_BASE = 8192;
    public static final int DEFAULT_CAP = 32768;
    public static final int DEFAULT_RETRIES = 1;

    public static OutputTokenLimits defaults() {
        return new OutputTokenLimits(DEFAULT_BASE, DEFAULT_CAP, DEFAULT_RETRIES);
    }

    public int limitForAttempt(int attempt) {
        if (attempt <= 0) {
            return base;
        }
        long scaled = (long) base * (1L << attempt);
        return (int) Math.min(scaled, cap);
    }

    public boolean canRetry(int attempt) {
        return attempt < maxRetries && limitForAttempt(attempt + 1) > limitForAttempt(attempt);
    }

    public static String retryMessage(int currentLimit, int nextLimit) {
        return "输出达到 token 上限 (" + currentLimit + ")，正在以 " + nextLimit + " 上限重试…";
    }

    public static String exhaustedMessage(int limit) {
        return "模型输出达到 token 上限 (" + limit + ")，内容可能不完整。"
                + "请缩小修改范围，或在 ~/.aicode/models.json 中提高 maxOutputTokens。";
    }
}

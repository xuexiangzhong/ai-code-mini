package com.aicode.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolOutputLimiterTest {
    @Test
    void leavesShortContentUnchanged() {
        assertEquals("ok", ToolOutputLimiter.limit("ok", 100));
    }

    @Test
    void truncatesLongContentWithHint() {
        String longText = "x".repeat(100);
        String result = ToolOutputLimiter.limit(longText, 50);
        assertTrue(result.startsWith("x".repeat(50)));
        assertTrue(result.contains("100 chars"));
    }

    @Test
    void wrapAppliesLimitOnExecutorResult() throws Exception {
        Agent.ToolExecutor inner = (name, input) ->
                java.util.concurrent.CompletableFuture.completedFuture("y".repeat(200));
        Agent.ToolExecutor limited = ToolOutputLimiter.wrap(inner, 80);
        String result = limited.execute("read_file", java.util.Map.of()).join();
        assertTrue(result.length() < 200);
        assertTrue(result.contains("truncated"));
    }
}

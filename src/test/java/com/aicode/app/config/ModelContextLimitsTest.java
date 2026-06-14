package com.aicode.app.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelContextLimitsTest {
    @Test
    void qwenMaxUsesLargeWindow() {
        assertEquals(131_072, ModelContextLimits.forModel("qwen3.7-max"));
        assertEquals(131_072, ModelContextLimits.forModel("qwen-max"));
    }

    @Test
    void deepseekChatUses64k() {
        assertEquals(64_000, ModelContextLimits.forModel("deepseek-chat"));
    }

    @Test
    void unknownModelFallsBackToDefault() {
        assertEquals(AppConfig.defaultContextWindow(), ModelContextLimits.forModel("unknown-model"));
    }
}

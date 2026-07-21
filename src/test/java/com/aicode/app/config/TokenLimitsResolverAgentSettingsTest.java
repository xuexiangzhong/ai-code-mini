package com.aicode.app.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenLimitsResolverAgentSettingsTest {
    @Test
    void appliesAgentSettingsFromProfile() {
        ModelProfile profile = new ModelProfile(
                "1", "Test", "https://api.example.com", "key", "deepseek-chat", "openai-compatible",
                null, null, null, null, 30, false, "nomic-embed-text"
        );
        AppConfig base = AppConfig.withDefaults().withValues(
                "key", "https://api.example.com", "deepseek-chat", "openai-compatible",
                "Agent", "🤖", java.nio.file.Path.of("/tmp")
        );
        AppConfig config = TokenLimitsResolver.applyProfileTokenLimits(base, profile);
        assertEquals(30, config.maxIterations());
        assertFalse(config.parallelToolCalls());
        assertEquals("nomic-embed-text", config.effectiveEmbeddingModel());
    }
}

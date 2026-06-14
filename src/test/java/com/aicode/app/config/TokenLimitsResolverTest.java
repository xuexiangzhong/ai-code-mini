package com.aicode.app.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class TokenLimitsResolverTest {
    @Test
    void profileCustomContextWindowWins() {
        ModelProfile profile = new ModelProfile(
                "1", "Test", "https://api.example.com", "key", "qwen3.7-max", "openai-compatible",
                65536, null, null, null
        );
        AppConfig base = AppConfig.withDefaults().withValues(
                "key", "https://api.example.com", "qwen3.7-max", "openai-compatible",
                "Agent", "🤖", Path.of("/tmp")
        );
        AppConfig config = TokenLimitsResolver.applyProfileTokenLimits(base, profile);
        assertEquals(65536, config.contextWindow());
    }

    @Test
    void autoDetectsFromModelWhenUnset() {
        ModelProfile profile = ModelProfile.of(
                "1", "Test", "https://api.example.com", "key", "qwen3.7-max", "openai-compatible"
        );
        AppConfig config = profile.toAppConfig(Path.of("/tmp/ws"));
        assertEquals(131_072, config.contextWindow());
    }

    @Test
    void usesDefaultContextWindowForUnknownModel() {
        ModelProfile profile = ModelProfile.of(
                "1", "Test", "https://api.example.com", "key", "unknown-model", "openai-compatible"
        );
        AppConfig config = profile.toAppConfig(Path.of("/tmp/ws"));
        assertEquals(AppConfig.defaultContextWindow(), config.contextWindow());
    }
}

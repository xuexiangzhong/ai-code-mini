package com.aicode.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        Path originalUserDir = Path.of(System.getProperty("user.dir"));
        try {
            System.setProperty("user.dir", tempDir.toString());
            AppConfig config = AppConfig.withDefaults().withValues(
                    "test-key-123",
                    "https://api.example.com",
                    "test-model",
                    "openai-compatible",
                    "Test",
                    "🧪",
                    tempDir
            );
            AppConfigStore.save(config);

            assertTrue(Files.exists(tempDir.resolve(AppConfigStore.FILE_NAME)));
            AppConfig loaded = AppConfigStore.load();
            assertEquals("test-key-123", loaded.apiKey());
            assertEquals("https://api.example.com", loaded.baseUrl());
            assertEquals("test-model", loaded.model());
            assertTrue(loaded.isConfigured());
        } finally {
            System.setProperty("user.dir", originalUserDir.toString());
        }
    }

    @Test
    void loadWithoutFileUsesDefaults() {
        AppConfig config = AppConfig.withDefaults();
        assertFalse(config.isConfigured());
    }
}

package com.aicode.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModelRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        Path originalUserDir = Path.of(System.getProperty("user.dir"));
        try {
            System.setProperty("user.dir", tempDir.toString());
            ModelRegistry registry = new ModelRegistry();
            ModelProfile profile = new ModelProfile(
                    "test-1", "Test", "https://api.example.com", "key-123", "gpt-test", "openai-compatible");
            registry.add(profile);
            registry.setDefaultModelId("test-1");
            registry.save();

            assertTrue(Files.exists(AicodePaths.modelsFile()));
            ModelRegistry loaded = ModelRegistry.load();
            assertEquals(1, loaded.models().size());
            assertEquals("key-123", loaded.defaultModel().apiKey());
            assertTrue(loaded.hasUsableModel());
        } finally {
            System.setProperty("user.dir", originalUserDir.toString());
        }
    }

    @Test
    void loadWithoutDirUsesDefaults() {
        ModelRegistry registry = ModelRegistry.load();
        assertNotNull(registry.defaultModel());
    }
}

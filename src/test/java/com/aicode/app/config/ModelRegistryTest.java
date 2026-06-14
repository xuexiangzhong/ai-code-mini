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
        String originalUserHome = System.getProperty("aicode.user.home");
        String originalUserDir = System.getProperty("user.dir");
        Path isolated = tempDir.resolve("save-load");
        try {
            System.setProperty("aicode.user.home", isolated.toString());
            System.setProperty("user.dir", isolated.toString());
            ModelRegistry registry = new ModelRegistry();
            ModelProfile profile = ModelProfile.of(
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
            if (originalUserHome != null) {
                System.setProperty("aicode.user.home", originalUserHome);
            } else {
                System.clearProperty("aicode.user.home");
            }
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    void loadWithoutDirUsesDefaults() {
        String originalUserHome = System.getProperty("aicode.user.home");
        String originalUserDir = System.getProperty("user.dir");
        Path isolated = tempDir.resolve("empty-home");
        try {
            System.setProperty("aicode.user.home", isolated.toString());
            System.setProperty("user.dir", isolated.toString());
            ModelRegistry registry = ModelRegistry.load();
            assertNotNull(registry.defaultModel());
        } finally {
            if (originalUserHome != null) {
                System.setProperty("aicode.user.home", originalUserHome);
            } else {
                System.clearProperty("aicode.user.home");
            }
            System.setProperty("user.dir", originalUserDir);
        }
    }
}

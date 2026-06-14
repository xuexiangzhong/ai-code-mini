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
                    "",
                    "",
                    "",
                    "",
                    "Test Agent",
                    "🧪",
                    tempDir.resolve("workspace")
            );
            AppConfigStore.save(config);

            assertTrue(Files.exists(tempDir.resolve(AppConfigStore.FILE_NAME)));
            String yaml = Files.readString(tempDir.resolve(AppConfigStore.FILE_NAME));
            assertFalse(yaml.contains("apiKey"));
            assertFalse(yaml.contains("baseUrl"));
            assertTrue(yaml.contains("Test Agent"));

            AppConfig loadedYaml = AppConfigStore.loadYaml();
            assertEquals("Test Agent", loadedYaml.agentName());
            assertEquals("🧪", loadedYaml.agentIcon());
            assertEquals(tempDir.resolve("workspace"), loadedYaml.workspace());
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

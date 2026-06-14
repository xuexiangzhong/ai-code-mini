package com.aicode.app.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Paths for AiCode data: user-global config vs project-local sessions. */
public final class AicodePaths {
    public static final String DIR_NAME = ".aicode";
    public static final String MODELS_FILE = "models.json";

    private AicodePaths() {}

    /** User-global directory: {@code ~/.aicode/} (model/API config). */
    public static Path userRoot() {
        return userHome().resolve(DIR_NAME);
    }

    public static Path modelsFile() {
        return userRoot().resolve(MODELS_FILE);
    }

    /** Project-local directory: {@code ./.aicode/} (sessions, indexes). */
    public static Path projectRoot() {
        return WorkingDirectory.effective().resolve(DIR_NAME);
    }

    /** Chat session files: {@code .aicode/sessions/{workspaceKey}/{sessionId}/}. */
    public static Path sessionsDir() {
        return projectRoot().resolve("sessions");
    }

    public static void ensureUserRootExists() {
        try {
            Files.createDirectories(userRoot());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create " + userRoot() + ": " + e.getMessage(), e);
        }
    }

    private static Path userHome() {
        String override = System.getProperty("aicode.user.home");
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
    }
}

package com.aicode.app.config;

import java.nio.file.Files;
import java.nio.file.Path;

/** Paths under {@code ./.aicode/} in the current working directory. */
public final class AicodePaths {
    public static final String DIR_NAME = ".aicode";
    public static final String MODELS_FILE = "models.json";

    private AicodePaths() {}

    public static Path root() {
        return WorkingDirectory.effective().resolve(DIR_NAME);
    }

    public static Path modelsFile() {
        return root().resolve(MODELS_FILE);
    }

    public static void ensureRootExists() {
        try {
            Files.createDirectories(root());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot create " + root() + ": " + e.getMessage(), e);
        }
    }
}

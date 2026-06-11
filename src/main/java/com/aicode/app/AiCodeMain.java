package com.aicode.app;

import com.aicode.app.ui.AiCodeApp;
import javafx.application.Application;

import java.util.Arrays;

/**
 * Unified entry point for AiCode.
 * Default: Hub + JavaFX desktop IDE / Agent windows
 * --cli: terminal REPL mode
 */
public final class AiCodeMain {
    private AiCodeMain() {}

    public static void main(String[] args) {
        com.aicode.app.config.WorkingDirectory.ensureSensible();
        if (Arrays.asList(args).contains("--cli")) {
            try {
                CliLauncher.run(args);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return;
        }
        Application.launch(AiCodeApp.class, args);
    }
}

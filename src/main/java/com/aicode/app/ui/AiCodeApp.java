package com.aicode.app.ui;

import com.aicode.app.config.ModelRegistry;
import com.aicode.app.window.WindowManager;
import javafx.application.Application;
import javafx.stage.Stage;

import java.io.IOException;

public final class AiCodeApp extends Application {
    private static WindowManager windowManager;

    public static WindowManager windowManager() {
        return windowManager;
    }

    @Override
    public void start(Stage stage) throws IOException {
        ModelRegistry registry = ModelRegistry.load();
        windowManager = new WindowManager(registry);
        windowManager.showHub(stage);
    }
}

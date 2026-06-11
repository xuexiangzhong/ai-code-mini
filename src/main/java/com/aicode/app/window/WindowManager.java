package com.aicode.app.window;

import com.aicode.app.config.ModelRegistry;
import com.aicode.app.config.WorkingDirectory;
import com.aicode.app.ui.AgentsWindowController;
import com.aicode.app.ui.AppIcons;
import com.aicode.app.ui.HubController;
import com.aicode.app.ui.MainWindowController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WindowManager {
    private final ModelRegistry modelRegistry;

    public WindowManager(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public ModelRegistry modelRegistry() {
        return modelRegistry;
    }

    public void showHub(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/hub.fxml"));
        loader.setControllerFactory(type -> {
            if (type == HubController.class) {
                return new HubController(this);
            }
            return newInstance(type);
        });
        Scene scene = new Scene(loader.load(), 760, 640);
        applyStyles(scene);
        stage.setTitle("AiCode");
        AppIcons.apply(stage);
        stage.setScene(scene);
        stage.show();
    }

    public void openProjectWindow(Path workspace, String modelId) throws IOException {
        Path root = WorkingDirectory.normalizeWorkspace(workspace);
        Stage stage = new Stage();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/main.fxml"));
        loader.setControllerFactory(type -> {
            if (type == MainWindowController.class) {
                return new MainWindowController(this, root, modelId);
            }
            return newInstance(type);
        });
        Scene scene = new Scene(loader.load(), 1280, 800);
        applyStyles(scene);
        stage.setTitle("AiCode — " + root.getFileName());
        AppIcons.apply(stage);
        stage.setScene(scene);
        stage.show();
    }

    public void openAgentsWindow(Path workspace, String modelId) throws IOException {
        Path root = WorkingDirectory.normalizeWorkspace(workspace);
        Stage stage = new Stage();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/agents.fxml"));
        loader.setControllerFactory(type -> {
            if (type == AgentsWindowController.class) {
                return new AgentsWindowController(this, root, modelId);
            }
            return newInstance(type);
        });
        Scene scene = new Scene(loader.load(), 1120, 780);
        applyAgentStyles(scene);
        stage.setTitle("Agent");
        AppIcons.apply(stage);
        stage.setScene(scene);
        stage.show();
    }

    private static void applyStyles(Scene scene) {
        scene.getStylesheets().add(WindowManager.class.getResource("/ui/styles.css").toExternalForm());
    }

    private static void applyAgentStyles(Scene scene) {
        scene.getStylesheets().add(WindowManager.class.getResource("/ui/agents.css").toExternalForm());
    }

    private static Object newInstance(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static Path chooseWorkspace(Stage owner) {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("选择工作空间");
        Path docs = WorkingDirectory.defaultWorkspace();
        if (Files.isDirectory(docs)) {
            chooser.setInitialDirectory(docs.toFile());
        }
        java.io.File selected = chooser.showDialog(owner);
        return selected != null ? selected.toPath().toAbsolutePath().normalize() : null;
    }
}

package com.aicode.app.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Optional;

public final class ModelSetupPromptDialog {
    public static final String GUIDE_URL = "https://aicode.xuesnow.icu/#guide";

    public enum Kind {
        WELCOME("欢迎使用 AiCode", "首次使用需配置大模型"),
        BLOCKED("尚未配置模型", "打开项目或 Agent 前，需先添加可用的大模型");

        private final String title;
        private final String subtitle;

        Kind(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    public enum Choice {
        ADD_MODEL,
        OPEN_GUIDE,
        DISMISS
    }

    private ModelSetupPromptDialog() {}

    public static Optional<Choice> show(Window owner, Kind kind) {
        Dialog<Choice> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(kind.title);
        dialog.getDialogPane().setHeader(null);
        dialog.getDialogPane().getStyleClass().add("setup-dialog-pane");

        Label title = new Label(kind.title);
        title.getStyleClass().add("setup-dialog-title");
        Label subtitle = new Label(kind.subtitle);
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("setup-dialog-subtitle");

        VBox steps = new VBox(6);
        steps.getStyleClass().add("hub-guide-card");
        steps.getChildren().addAll(
                step("1. API URL — 模型服务接口地址"),
                step("   Ollama: http://localhost:11434/v1"),
                step("   通义千问: https://dashscope.aliyuncs.com/compatible-mode/v1"),
                step("2. Model — 具体模型名称（如 qwen3:8b、deepseek-chat）"),
                step("3. API Key — Ollama 可填任意值，云端服务填 sk- 开头的 Key")
        );

        Label footer = new Label("兼容 OpenAI API 格式的服务均可接入（Ollama、DeepSeek、通义千问等）");
        footer.setWrapText(true);
        footer.getStyleClass().add("hint-text");

        VBox content = new VBox(12, title, subtitle, steps, footer);
        content.setPadding(new Insets(4, 0, 0, 0));
        content.getStyleClass().add("setup-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(500);

        ButtonType add = new ButtonType("立即添加模型", ButtonBar.ButtonData.OK_DONE);
        ButtonType guide = new ButtonType("查看图文教程");
        ButtonType dismiss = kind == Kind.WELCOME
                ? new ButtonType("稍后", ButtonBar.ButtonData.CANCEL_CLOSE)
                : ButtonType.CANCEL;
        dialog.getDialogPane().getButtonTypes().addAll(add, guide, dismiss);

        dialog.setResultConverter(button -> {
            if (button == add) {
                return Choice.ADD_MODEL;
            }
            if (button == guide) {
                return Choice.OPEN_GUIDE;
            }
            return Choice.DISMISS;
        });

        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                applyStyles(owner, newScene);
            }
        });

        dialog.setOnShown(e -> styleButtons(dialog, add, guide, dismiss));

        return dialog.showAndWait();
    }

    private static Label step(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("guide-step");
        return label;
    }

    private static void applyStyles(Window owner, javafx.scene.Scene scene) {
        if (owner instanceof Stage stage && stage.getScene() != null) {
            scene.getStylesheets().addAll(stage.getScene().getStylesheets());
        }
    }

    private static void styleButtons(Dialog<Choice> dialog, ButtonType add, ButtonType guide, ButtonType dismiss) {
        styleButton(dialog, add, "primary-button");
        styleButton(dialog, guide, "ghost-button");
        styleButton(dialog, dismiss, "ghost-button");
    }

    private static void styleButton(Dialog<Choice> dialog, ButtonType type, String styleClass) {
        Button button = (Button) dialog.getDialogPane().lookupButton(type);
        if (button != null) {
            button.getStyleClass().add(styleClass);
        }
    }
}

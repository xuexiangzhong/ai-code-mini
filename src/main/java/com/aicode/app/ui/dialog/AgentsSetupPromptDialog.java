package com.aicode.app.ui.dialog;

import com.aicode.app.config.AgentsMdSetup;
import com.aicode.app.config.AgentsMdTemplate;
import com.aicode.app.config.AgentsMdTemplates;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.Optional;

public final class AgentsSetupPromptDialog {
    public enum Kind {
        WELCOME("配置 AGENTS.md", "为 Agent 添加项目专属指令"),
        BROWSE("AGENTS.md 预设模板", "选择模板预览，打开项目时可一键写入工作区根目录");

        private final String title;
        private final String subtitle;

        Kind(String title, String subtitle) {
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    public enum Choice {
        CREATED,
        DISMISS
    }

    public record Result(Choice choice, AgentsMdTemplate template) {}

    private AgentsSetupPromptDialog() {}

    public static Optional<Result> show(Window owner, Kind kind, Path workspace) {
        Dialog<Result> dialog = new Dialog<>();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(kind.title);
        dialog.getDialogPane().setHeader(null);
        dialog.getDialogPane().getStyleClass().add("setup-dialog-pane");

        Label title = new Label(kind.title);
        title.getStyleClass().add("setup-dialog-title");
        Label subtitle = new Label(buildSubtitle(kind, workspace));
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("setup-dialog-subtitle");

        VBox intro = new VBox(6);
        intro.getStyleClass().add("hub-guide-card");
        intro.getChildren().addAll(
                hint("AGENTS.md 放在项目根目录，Agent 启动时自动读取"),
                hint("可定义技术栈、编码规范、测试命令与协作偏好"),
                hint("优先级高于 CLAUDE.md、README.md 与 .cursor/rules")
        );

        ComboBox<AgentsMdTemplate> templateBox = new ComboBox<>();
        templateBox.getItems().addAll(AgentsMdTemplates.all());
        templateBox.setCellFactory(list -> templateCell());
        templateBox.setButtonCell(templateCell());
        templateBox.getSelectionModel().select(AgentsMdTemplates.defaultTemplate());
        templateBox.setMaxWidth(Double.MAX_VALUE);

        TextArea preview = new TextArea();
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(14);
        VBox.setVgrow(preview, Priority.ALWAYS);
        preview.getStyleClass().add("agents-template-preview");

        Runnable updatePreview = () -> {
            AgentsMdTemplate selected = templateBox.getSelectionModel().getSelectedItem();
            if (selected != null) {
                preview.setText(selected.content());
            }
        };
        templateBox.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> updatePreview.run());
        updatePreview.run();

        Label templateHint = new Label();
        templateHint.setWrapText(true);
        templateHint.getStyleClass().add("hint-text");
        templateBox.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                templateHint.setText(selected.description());
            }
        });
        AgentsMdTemplate initial = templateBox.getSelectionModel().getSelectedItem();
        if (initial != null) {
            templateHint.setText(initial.description());
        }

        VBox content = new VBox(12, title, subtitle, intro, templateBox, templateHint, preview);
        content.setPadding(new Insets(4, 0, 0, 0));
        content.getStyleClass().add("setup-dialog-content");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().setPrefHeight(620);

        boolean canCreate = workspace != null && !AgentsMdSetup.hasAgentsMd(workspace);
        ButtonType create = new ButtonType(
                canCreate ? "创建 AGENTS.md" : "关闭",
                canCreate ? ButtonBar.ButtonData.OK_DONE : ButtonBar.ButtonData.CANCEL_CLOSE
        );
        ButtonType dismiss = canCreate ? new ButtonType("稍后", ButtonBar.ButtonData.CANCEL_CLOSE) : null;
        if (dismiss != null) {
            dialog.getDialogPane().getButtonTypes().addAll(create, dismiss);
        } else {
            dialog.getDialogPane().getButtonTypes().add(create);
        }

        dialog.setResultConverter(button -> {
            if (button == create && canCreate) {
                AgentsMdTemplate selected = templateBox.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    return new Result(Choice.DISMISS, null);
                }
                try {
                    AgentsMdSetup.writeTemplate(workspace, selected);
                    return new Result(Choice.CREATED, selected);
                } catch (Exception e) {
                    preview.setText("创建失败: " + e.getMessage() + "\n\n" + preview.getText());
                    return null;
                }
            }
            return new Result(Choice.DISMISS, templateBox.getSelectionModel().getSelectedItem());
        });

        dialog.getDialogPane().sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                applyStyles(owner, newScene);
            }
        });

        ButtonType finalDismiss = dismiss;
        dialog.setOnShown(e -> {
            styleButton(dialog, create, canCreate ? "primary-button" : "ghost-button");
            if (finalDismiss != null) {
                styleButton(dialog, finalDismiss, "ghost-button");
            }
        });

        return dialog.showAndWait();
    }

    public static void promptIfMissing(Window owner, Path workspace) {
        if (workspace == null || AgentsMdSetup.hasAgentsMd(workspace)) {
            return;
        }
        show(owner, Kind.WELCOME, workspace);
    }

    private static String buildSubtitle(Kind kind, Path workspace) {
        if (kind == Kind.BROWSE) {
            return kind.subtitle;
        }
        if (workspace == null) {
            return kind.subtitle;
        }
        return kind.subtitle + "\n工作区: " + workspace;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.getStyleClass().add("guide-step");
        return label;
    }

    private static ListCell<AgentsMdTemplate> templateCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AgentsMdTemplate item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        };
    }

    private static void applyStyles(Window owner, javafx.scene.Scene scene) {
        if (owner instanceof Stage stage && stage.getScene() != null) {
            scene.getStylesheets().addAll(stage.getScene().getStylesheets());
        }
    }

    private static void styleButton(Dialog<Result> dialog, ButtonType type, String styleClass) {
        Button button = (Button) dialog.getDialogPane().lookupButton(type);
        if (button != null) {
            button.getStyleClass().add(styleClass);
        }
    }
}

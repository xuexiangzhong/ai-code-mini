package com.aicode.app.ui.dialog;

import com.aicode.app.config.ModelProfile;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Optional;

public final class ModelEditDialog {
    private ModelEditDialog() {}

    public static Optional<ModelProfile> show(Window owner, ModelProfile initial) {
        Dialog<ModelProfile> dialog = new Dialog<>();
        dialog.setTitle("新模型".equals(initial.name()) ? "添加模型" : "编辑模型");
        dialog.initOwner(owner);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nameField = new TextField(initial.name());
        nameField.setPromptText("便于识别的名称，如「本地 Ollama」");
        TextField urlField = new TextField(initial.baseUrl());
        urlField.setPromptText("如 http://localhost:11434/v1 或 https://dashscope.aliyuncs.com/compatible-mode/v1");
        PasswordField keyField = new PasswordField();
        keyField.setText(initial.apiKey());
        keyField.setPromptText("Ollama 可填 ollama，云端服务填 sk- 开头的 Key");
        TextField modelField = new TextField(initial.model());
        modelField.setPromptText("如 qwen3:8b、deepseek-chat、qwen3-235b-a22b");
        TextField providerField = new TextField(initial.providerType());
        providerField.setPromptText("通常填 openai-compatible，一般无需修改");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        grid.add(new Label("名称"), 0, 0);
        grid.add(nameField, 1, 0);
        grid.add(new Label("API URL"), 0, 1);
        grid.add(urlField, 1, 1);
        grid.add(new Label("API Key"), 0, 2);
        grid.add(keyField, 1, 2);
        grid.add(new Label("Model"), 0, 3);
        grid.add(modelField, 1, 3);
        grid.add(new Label("Provider"), 0, 4);
        grid.add(providerField, 1, 4);
        Label hint = new Label(
                "兼容 OpenAI API 格式的服务均可接入（Ollama、DeepSeek、通义千问等）。"
                        + " 图文教程: https://aicode.xuesnow.icu/#guide"
        );
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #6e6e6e; -fx-font-size: 11px;");
        grid.add(hint, 0, 5, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            return initial.withValues(
                    nameField.getText().strip(),
                    urlField.getText().strip(),
                    keyField.getText().strip(),
                    modelField.getText().strip(),
                    providerField.getText().strip()
            );
        });

        return dialog.showAndWait();
    }
}

package com.aicode.app.ui.dialog;

import com.aicode.app.config.AppConfig;
import com.aicode.app.config.ModelContextLimits;
import com.aicode.app.config.ModelProfile;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
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
        providerField.setPromptText("通常填 openai-compatible，Anthropic 填 anthropic");
        TextField contextWindowField = new TextField(
                initial.contextWindow() != null && initial.contextWindow() > 0
                        ? String.valueOf(initial.contextWindow())
                        : ""
        );
        contextWindowField.setPromptText("留空则按模型名自动推断，如 qwen-max → 128k");
        Label contextHint = new Label("自动推断: " + formatTokens(ModelContextLimits.forModel(initial.model())));
        contextHint.setStyle("-fx-text-fill: #6e6e6e; -fx-font-size: 11px;");
        modelField.textProperty().addListener((obs, old, model) -> {
            String m = model != null ? model.strip() : "";
            contextHint.setText(m.isEmpty()
                    ? "自动推断: 32k"
                    : "自动推断: " + formatTokens(ModelContextLimits.forModel(m)));
        });

        TextField embeddingField = new TextField(
                initial.embeddingModel() != null ? initial.embeddingModel() : ""
        );
        embeddingField.setPromptText("留空则用 text-embedding-3-small；Ollama 可填 nomic-embed-text");

        TextField maxIterationsField = new TextField(
                initial.maxIterations() != null && initial.maxIterations() > 0
                        ? String.valueOf(initial.maxIterations())
                        : String.valueOf(AppConfig.withDefaults().maxIterations())
        );
        maxIterationsField.setPromptText("Agent 单轮最大工具迭代次数，默认 20");

        CheckBox parallelToolsBox = new CheckBox("并行执行多个工具调用");
        parallelToolsBox.setSelected(
                initial.parallelToolCalls() != null
                        ? initial.parallelToolCalls()
                        : AppConfig.withDefaults().parallelToolCalls()
        );

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(12));
        int row = 0;
        grid.add(new Label("名称"), 0, row);
        grid.add(nameField, 1, row++);
        grid.add(new Label("API URL"), 0, row);
        grid.add(urlField, 1, row++);
        grid.add(new Label("API Key"), 0, row);
        grid.add(keyField, 1, row++);
        grid.add(new Label("Model"), 0, row);
        grid.add(modelField, 1, row++);
        grid.add(new Label("Provider"), 0, row);
        grid.add(providerField, 1, row++);
        grid.add(new Label("上下文窗口"), 0, row);
        grid.add(contextWindowField, 1, row++);
        grid.add(contextHint, 1, row++);
        grid.add(new Label("Embedding 模型"), 0, row);
        grid.add(embeddingField, 1, row++);
        grid.add(new Label("Agent 迭代上限"), 0, row);
        grid.add(maxIterationsField, 1, row++);
        grid.add(parallelToolsBox, 1, row++);
        Label hint = new Label(
                "兼容 OpenAI API 格式的服务均可接入（Ollama、DeepSeek、通义千问等）。"
                        + " Anthropic 填 provider=anthropic，URL 可留空。"
                        + " 图文教程: https://aicode.xuesnow.icu/#guide"
        );
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #6e6e6e; -fx-font-size: 11px;");
        grid.add(hint, 0, row, 2, 1);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(520);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            return initial.withExtendedValues(
                    nameField.getText().strip(),
                    urlField.getText().strip(),
                    keyField.getText().strip(),
                    modelField.getText().strip(),
                    providerField.getText().strip(),
                    parseContextWindow(contextWindowField.getText()),
                    parsePositiveInt(maxIterationsField.getText(), AppConfig.withDefaults().maxIterations()),
                    parallelToolsBox.isSelected(),
                    embeddingField.getText().strip()
            );
        });

        return dialog.showAndWait();
    }

    private static Integer parseContextWindow(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.strip().toLowerCase();
        if (normalized.endsWith("k")) {
            try {
                return Integer.parseInt(normalized.substring(0, normalized.length() - 1)) * 1000;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        try {
            int value = Integer.parseInt(normalized);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parsePositiveInt(String text, int defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(text.strip());
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String formatTokens(int value) {
        if (value >= 1000 && value % 1000 == 0) {
            return (value / 1000) + "k";
        }
        return String.valueOf(value);
    }
}

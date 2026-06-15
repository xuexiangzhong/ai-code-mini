package com.aicode.app.ui;

import com.aicode.app.config.AicodePaths;
import com.aicode.app.config.ModelProfile;
import com.aicode.app.config.ModelRegistry;
import com.aicode.app.ui.dialog.AgentsSetupPromptDialog;
import com.aicode.app.ui.dialog.ModelEditDialog;
import com.aicode.app.ui.dialog.ModelSetupPromptDialog;
import com.aicode.app.ui.dialog.ModelSetupPromptDialog.Choice;
import com.aicode.app.ui.dialog.ModelSetupPromptDialog.Kind;
import com.aicode.app.window.WindowManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HubController {
    private final WindowManager windowManager;

    @FXML private Label configPathLabel;
    @FXML private VBox onboardingBox;
    @FXML private Button quickAddButton;
    @FXML private Button openGuideButton;
    @FXML private ListView<ModelProfile> modelList;
    @FXML private Label statusLabel;
    @FXML private Button addModelButton;
    @FXML private Button editModelButton;
    @FXML private Button deleteModelButton;
    @FXML private Button defaultModelButton;
    @FXML private Button openConfigFolderButton;
    @FXML private Button browseAgentsTemplatesButton;
    @FXML private Button openProjectButton;
    @FXML private Button openAgentsButton;

    public HubController(WindowManager windowManager) {
        this.windowManager = windowManager;
    }

    @FXML
    void initialize() {
        configPathLabel.setText(AicodePaths.modelsFile().toString());
        openConfigFolderButton.setOnAction(e -> openConfigFolder());
        modelList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(ModelProfile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String mark = item.id().equals(registry().defaultModelId()) ? "★ " : "  ";
                String key = item.isUsable() ? "" : " [未配置 Key]";
                setText(mark + item.displayLabel() + " · " + item.contextWindowLabel() + key);
            }
        });
        addModelButton.setOnAction(e -> addModel());
        quickAddButton.setOnAction(e -> addModel());
        openGuideButton.setOnAction(e -> openGuideInBrowser());
        browseAgentsTemplatesButton.setOnAction(e -> browseAgentsTemplates());
        editModelButton.setOnAction(e -> editModel());
        deleteModelButton.setOnAction(e -> deleteModel());
        defaultModelButton.setOnAction(e -> setDefault());
        openProjectButton.setOnAction(e -> openProject());
        openAgentsButton.setOnAction(e -> openAgents());
        refreshModels();
        maybeShowFirstRunGuide();
    }

    public void refreshModels() {
        modelList.setItems(FXCollections.observableArrayList(registry().models()));
        if (!registry().models().isEmpty()) {
            modelList.getSelectionModel().select(registry().defaultModel());
        }
        updateStatus();
    }

    private void addModel() {
        ModelEditDialog.show(stage(), ModelRegistry.newProfile()).ifPresent(profile -> {
            registry().add(profile);
            persist();
        });
    }

    private void editModel() {
        ModelProfile selected = modelList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("请先选择一个模型");
            return;
        }
        ModelEditDialog.show(stage(), selected).ifPresent(profile -> {
            registry().update(profile);
            persist();
        });
    }

    private void deleteModel() {
        ModelProfile selected = modelList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        registry().remove(selected.id());
        persist();
    }

    private void setDefault() {
        ModelProfile selected = modelList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        registry().setDefaultModelId(selected.id());
        persist();
    }

    private void openProject() {
        if (!ensureModelConfigured()) {
            return;
        }
        Path workspace = WindowManager.chooseWorkspace(stage());
        if (workspace == null) {
            return;
        }
        promptAgentsMdIfMissing(workspace);
        try {
            windowManager.openProjectWindow(workspace, resolveModelId());
        } catch (Exception e) {
            statusLabel.setText("打开项目窗口失败: " + rootCause(e));
            e.printStackTrace();
        }
    }

    private void openAgents() {
        if (!ensureModelConfigured()) {
            return;
        }
        Path workspace = WindowManager.chooseWorkspace(stage());
        if (workspace == null) {
            return;
        }
        promptAgentsMdIfMissing(workspace);
        try {
            windowManager.openAgentsWindow(workspace, resolveModelId());
        } catch (Exception e) {
            statusLabel.setText("打开 Agent 窗口失败: " + rootCause(e));
            e.printStackTrace();
        }
    }

    private boolean ensureModelConfigured() {
        if (registry().hasUsableModel()) {
            return true;
        }
        handleSetupChoice(ModelSetupPromptDialog.show(stage(), Kind.BLOCKED));
        return false;
    }

    private void maybeShowFirstRunGuide() {
        if (registry().hasUsableModel() || Files.isRegularFile(AicodePaths.modelsFile())) {
            return;
        }
        Platform.runLater(() -> handleSetupChoice(ModelSetupPromptDialog.show(stage(), Kind.WELCOME)));
    }

    private void handleSetupChoice(java.util.Optional<Choice> choice) {
        choice.ifPresent(c -> {
            switch (c) {
                case ADD_MODEL -> addModel();
                case OPEN_GUIDE -> openGuideInBrowser();
                case DISMISS -> {}
            }
        });
    }

    private void openConfigFolder() {
        try {
            AicodePaths.ensureUserRootExists();
            Path dir = AicodePaths.userRoot();
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir.toFile());
            } else {
                statusLabel.setText("请手动打开: " + dir);
            }
        } catch (Exception e) {
            statusLabel.setText("无法打开文件夹: " + e.getMessage());
        }
    }

    private void browseAgentsTemplates() {
        AgentsSetupPromptDialog.show(stage(), AgentsSetupPromptDialog.Kind.BROWSE, null);
    }

    private void promptAgentsMdIfMissing(Path workspace) {
        AgentsSetupPromptDialog.promptIfMissing(stage(), workspace);
    }

    private void openGuideInBrowser() {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(ModelSetupPromptDialog.GUIDE_URL));
            } else {
                statusLabel.setText("请手动打开浏览器访问: " + ModelSetupPromptDialog.GUIDE_URL);
            }
        } catch (Exception e) {
            statusLabel.setText("无法打开浏览器，请手动访问: " + ModelSetupPromptDialog.GUIDE_URL);
        }
    }

    private static String rootCause(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }

    private String resolveModelId() {
        ModelProfile selected = modelList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected.id();
        }
        return registry().defaultModelId();
    }

    private void persist() {
        try {
            registry().save();
            refreshModels();
        } catch (IOException e) {
            statusLabel.setText("保存失败: " + e.getMessage());
        }
    }

    private void updateStatus() {
        boolean needsSetup = !registry().hasUsableModel();
        onboardingBox.setVisible(needsSetup);
        onboardingBox.setManaged(needsSetup);

        if (registry().models().isEmpty()) {
            statusLabel.setText("尚未配置模型。点击上方「立即添加模型」，或设置 DEEPSEEK_API_KEY / OPENAI_API_KEY 环境变量后重启。");
        } else if (!registry().hasUsableModel()) {
            statusLabel.setText("已有模型条目，但缺少 API Key。请编辑并填写 Key 后保存。");
        } else {
            statusLabel.setText("已加载 " + registry().models().size() + " 个模型，默认: "
                    + registry().defaultModel().displayLabel());
        }
    }

    private ModelRegistry registry() {
        return windowManager.modelRegistry();
    }

    private Stage stage() {
        return (Stage) modelList.getScene().getWindow();
    }
}

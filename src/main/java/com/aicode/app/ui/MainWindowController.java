package com.aicode.app.ui;

import com.aicode.app.application.WorkspaceGuard;
import com.aicode.app.config.ModelProfile;
import com.aicode.app.config.ModelRegistry;
import com.aicode.app.config.WorkingDirectory;
import com.aicode.app.ui.pane.EditorTabManager;
import com.aicode.app.ui.pane.TerminalPane;
import com.aicode.app.window.WindowManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MainWindowController {
    private final WindowManager windowManager;
    private final Path initialWorkspace;
    private final String initialModelId;
    private AgentSessionBinder binder;

    @FXML private TextField workspaceField;
    @FXML private Button selectWorkspaceButton;
    @FXML private Button saveButton;
    @FXML private Button refreshFileTreeButton;
    @FXML private TreeView<String> fileTree;
    @FXML private VBox editorHost;
    @FXML private VBox terminalHost;
    @FXML private ComboBox<ModelProfile> modelBox;
    @FXML private Button hubButton;
    @FXML private ListView<ConversationContext> conversationList;
    @FXML private Button newConversationButton;
    @FXML private VBox chatMessagesHost;
    @FXML private VBox chatInputHost;
    private ChatTranscriptView chatView;
    private AtMentionInput composerInput;
    @FXML private Button sendButton;
    @FXML private ComboBox<com.aicode.app.session.ChatMode> chatModeBox;
    @FXML private Label statusLabel;

    private Path workspaceRoot;
    private EditorTabManager editorTabs;
    private TerminalPane terminalPane;
    private WorkspaceFileTreeRefresher fileTreeRefresher;

    public MainWindowController(WindowManager windowManager, Path workspace, String modelId) {
        this.windowManager = windowManager;
        this.initialWorkspace = workspace;
        this.initialModelId = modelId;
    }

    @FXML
    void initialize() {
        workspaceRoot = initialWorkspace;
        workspaceField.setText(workspaceRoot.toString());

        fileTreeRefresher = new WorkspaceFileTreeRefresher(
                fileTree,
                workspaceRoot,
                null,
                () -> workspaceRoot != null
        );
        refreshFileTreeButton.setOnAction(e -> fileTreeRefresher.refresh());
        fileTreeRefresher.startAutoRefresh();

        fileTree.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null && selected.getValue() != null) {
                openSelectedFile(workspaceRoot, selected);
            }
        });
        loadFileTreeAsync(workspaceRoot);

        editorTabs = new EditorTabManager(workspaceGuard());
        editorTabs.setOnStatus(msg -> Platform.runLater(() -> statusLabel.setText(msg)));
        VBox.setVgrow(editorTabs, Priority.ALWAYS);
        editorHost.getChildren().add(editorTabs);

        terminalPane = new TerminalPane();
        terminalPane.startShell(workspaceRoot);
        VBox.setVgrow(terminalPane, Priority.ALWAYS);
        terminalHost.getChildren().add(terminalPane);

        chatView = new ChatTranscriptView();
        VBox.setVgrow(chatView, Priority.ALWAYS);
        chatMessagesHost.getChildren().add(chatView);

        composerInput = createComposerInput(workspaceRoot);
        HBox.setHgrow(composerInput.node(), Priority.ALWAYS);
        chatInputHost.getChildren().add(composerInput);

        selectWorkspaceButton.setOnAction(e -> chooseWorkspace());
        saveButton.setOnAction(e -> editorTabs.saveActiveFile());

        binder = new AgentSessionBinder(
                workspaceRoot,
                () -> (Stage) sendButton.getScene().getWindow(),
                chatView,
                composerInput,
                sendButton,
                chatModeBox,
                statusLabel,
                () -> appendChat("请先在「模型配置」主界面添加模型并填写 API Key。\n\n")
        );
        binder.setToolLogConsumer(terminalPane::appendToolLog);
        binder.bindConversations(conversationList, newConversationButton);
        binder.initializeModes();
        hubButton.setOnAction(e -> binder.appendSystemLine("请在「AiCode — 模型配置」主窗口中管理模型。"));
        populateModels();
        selectInitialModel();

        binder.appendSystemLine("工作空间: " + workspaceRoot);
        binder.appendSystemLine("Agent 读写文件限制在工作空间内；越界路径会被 sandbox 拦截。");
        binder.appendSystemLine("快捷键: Ctrl+S 保存 | Ctrl+L 聚焦 Chat | 输入 @ 引用文件/选中代码");

        statusLabel.setText("Workspace: " + workspaceRoot);
        installShortcuts();
    }

    private void installShortcuts() {
        editorHost.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                return;
            }
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                    editorTabs::saveActiveFile
            );
            scene.getAccelerators().put(
                    new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                    () -> composerInput.requestFocus()
            );
        });
    }

    private void loadFileForContext(Path path, java.util.function.Consumer<String> callback) {
        if (editorTabs.activeFile().map(path::equals).orElse(false)) {
            editorTabs.editorPane().getContentAsync()
                    .thenAccept(content -> Platform.runLater(() -> callback.accept(content)));
            return;
        }
        Thread.ofVirtual().name("context-file-load").start(() -> {
            try {
                String content = Files.readString(path);
                Platform.runLater(() -> callback.accept(content));
            } catch (IOException e) {
                Platform.runLater(() -> callback.accept(""));
            }
        });
    }

    private void populateModels() {
        ModelRegistry registry = windowManager.modelRegistry();
        modelBox.setCellFactory(cb -> createModelCell());
        modelBox.setButtonCell(createModelCell());
        modelBox.getItems().setAll(registry.models());
        modelBox.valueProperty().addListener((obs, old, model) -> {
            if (binder != null) {
                binder.applyModel(model);
            }
        });
    }

    private static ListCell<ModelProfile> createModelCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(ModelProfile item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayLabel());
            }
        };
    }

    private void selectInitialModel() {
        ModelRegistry registry = windowManager.modelRegistry();
        if (registry.models().isEmpty()) {
            binder.applyModel(null);
            return;
        }
        ModelProfile initial = registry.findById(initialModelId)
                .orElseGet(registry::defaultModel);
        if (registry.findById(initial.id()).isPresent()) {
            modelBox.setValue(initial);
        } else {
            modelBox.getSelectionModel().selectFirst();
        }
        binder.applyModel(modelBox.getValue());
    }

    private void chooseWorkspace() {
        Path selected = WindowManager.chooseWorkspace((Stage) workspaceField.getScene().getWindow());
        if (selected == null) {
            return;
        }
        applyWorkspace(selected);
    }

    private void applyWorkspace(Path path) {
        if (!Files.isDirectory(path)) {
            binder.appendSystemLine("无效的工作空间目录: " + path);
            return;
        }
        workspaceRoot = path;
        workspaceField.setText(path.toString());
        fileTreeRefresher.setWorkspace(workspaceRoot);
        loadFileTreeAsync(workspaceRoot);
        statusLabel.setText("Workspace: " + workspaceRoot);
        editorTabs = new EditorTabManager(workspaceGuard());
        editorTabs.setOnStatus(msg -> Platform.runLater(() -> statusLabel.setText(msg)));
        VBox.setVgrow(editorTabs, Priority.ALWAYS);
        editorHost.getChildren().setAll(editorTabs);
        rebindComposer(path);
        terminalPane.stopShell();
        terminalPane.startShell(workspaceRoot);
        binder.updateWorkspace(workspaceRoot);
        binder.applyModel(modelBox.getValue());
        binder.appendSystemLine("工作空间已切换: " + workspaceRoot);
    }

    private AtMentionInput createComposerInput(Path workspace) {
        AtMentionInput input = new AtMentionInput();
        input.setPromptText("输入问题或编程任务…（@ 引用文件，Enter 发送，Shift+Enter 换行）");
        input.bindWorkspace(workspace);
        input.setActiveFileSupplier(() -> editorTabs.activeFile().orElse(null));
        input.setEditorPaneSupplier(() -> editorTabs.editorPane());
        input.setFileLoader(this::loadFileForContext);
        return input;
    }

    private void rebindComposer(Path workspace) {
        composerInput.bindWorkspace(workspace);
        composerInput.setActiveFileSupplier(() -> editorTabs.activeFile().orElse(null));
        composerInput.setEditorPaneSupplier(() -> editorTabs.editorPane());
        composerInput.setFileLoader(this::loadFileForContext);
    }

    private WorkspaceGuard workspaceGuard() {
        return new WorkspaceGuard(
                workspaceRoot,
                new com.aicode.agent.Safety.FileSystemSandbox(
                        java.util.List.of(workspaceRoot.toString(), System.getProperty("java.io.tmpdir"))
                )
        );
    }

    void appendChat(String text) {
        Platform.runLater(() -> chatView.appendStandaloneNotice(text.strip()));
    }

    private void loadFileTreeAsync(Path root) {
        WorkspaceFileTree.loadAsync(fileTree, root, null);
    }

    private void openSelectedFile(Path root, TreeItem<String> selected) {
        String label = selected.getValue();
        if (label == null || "…".equals(label) || label.startsWith("(无法读取") || "加载中…".equals(label)) {
            return;
        }
        Path resolved = WorkspaceFileTree.resolvePath(root, fileTree.getRoot(), selected);
        if (resolved == null || !Files.isRegularFile(resolved)) {
            return;
        }
        try {
            String content = Files.readString(resolved);
            editorTabs.openFile(resolved, content);
        } catch (IOException e) {
            binder.appendSystemLine("Failed to open file: " + e.getMessage());
        }
    }
}

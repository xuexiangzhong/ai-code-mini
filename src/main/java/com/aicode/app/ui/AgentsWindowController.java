package com.aicode.app.ui;

import com.aicode.app.application.WorkspaceGuard;
import com.aicode.app.config.ModelProfile;
import com.aicode.app.config.ModelRegistry;
import com.aicode.app.config.WorkingDirectory;
import com.aicode.app.ui.pane.EditorFileContent;
import com.aicode.app.ui.pane.EditorFileLoader;
import com.aicode.app.ui.pane.EditorTabManager;
import com.aicode.app.window.WindowManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AgentsWindowController {
    private static final double EDGE_STRIP_WIDTH = 12.0;

    private final WindowManager windowManager;
    private final Path initialWorkspace;
    private final String initialModelId;
    private AgentWindowSessionManager sessionManager;

    @FXML private Label threadTitleLabel;
    @FXML private Label footerLabel;
    @FXML private ScrollPane sidebarScroll;
    @FXML private VBox sidebarNavBox;
    @FXML private Button addWorkspaceButton;
    @FXML private VBox filePanel;
    @FXML private Label filePanelTitle;
    @FXML private Button saveFileButton;
    @FXML private Button refreshFileTreeButton;
    @FXML private Button closeFilePanelButton;
    @FXML private Button toggleFilePanelButton;
    @FXML private SplitPane centerSplit;
    @FXML private StackPane editorSlot;
    @FXML private VBox editorDock;
    @FXML private Button collapseEditorButton;
    @FXML private Button closeEditorDockButton;
    @FXML private Region editorEdgeStrip;
    @FXML private TreeView<String> fileTree;
    @FXML private VBox agentEditorHost;
    @FXML private ComboBox<ModelProfile> modelBox;
    @FXML private Button hubButton;
    @FXML private ComboBox<com.aicode.app.session.ChatMode> chatModeBox;
    @FXML private VBox chatMessagesHost;
    private ChatTranscriptView chatView;
    @FXML private VBox chatInputHost;
    private AtMentionInput composerInput;
    @FXML private Button sendButton;

    private enum EditorLayout {
        HIDDEN, EXPANDED, STRIP
    }

    private Path activeWorkspace;
    private Path editorWorkspace;
    private EditorTabManager editorTabs;
    private WorkspaceFileTreeRefresher fileTreeRefresher;
    private boolean filePanelVisible;
    private EditorLayout editorLayout = EditorLayout.HIDDEN;

    public AgentsWindowController(WindowManager windowManager, Path workspaceRoot, String modelId) {
        this.windowManager = windowManager;
        this.initialWorkspace = WorkingDirectory.normalizeWorkspace(workspaceRoot);
        this.initialModelId = modelId;
        this.activeWorkspace = this.initialWorkspace;
    }

    @FXML
    void initialize() {
        threadTitleLabel.setText("新对话");
        footerLabel.setText(initialWorkspace.toString());

        AgentSidebarNav sidebarNav = new AgentSidebarNav(sidebarNavBox, sidebarScroll, addWorkspaceButton);

        chatView = new ChatTranscriptView();
        VBox.setVgrow(chatView, Priority.ALWAYS);
        chatMessagesHost.getChildren().add(chatView);
        chatView.setOnFileEditChanged(this::reloadEditedFile);

        composerInput = new AtMentionInput();
        composerInput.setPromptText("发送消息…（@ 引用 · 粘贴/拖入图片 · Enter 发送，Shift+Enter 换行）");
        composerInput.bindWorkspace(initialWorkspace);
        HBox.setHgrow(composerInput.node(), Priority.ALWAYS);
        chatInputHost.getChildren().add(composerInput);

        sessionManager = new AgentWindowSessionManager(
                () -> (Stage) sendButton.getScene().getWindow(),
                chatView,
                composerInput,
                sendButton,
                chatModeBox,
                threadTitleLabel,
                footerLabel,
                () -> appendChat("请先在「模型配置」主界面添加模型并填写 API Key。\n\n")
        );
        sessionManager.setOnWorkspaceActivated(this::onWorkspaceActivated);
        sessionManager.setOnMessageSent(this::collapseEditorToStrip);
        sessionManager.setOnFileDiskChanged(this::reloadEditedFile);
        sessionManager.bindSidebarNav(sidebarNav);
        sessionManager.initializeModes();

        saveFileButton.setOnAction(e -> {
            if (editorTabs != null) {
                editorTabs.saveActiveFile();
            }
        });
        collapseEditorButton.setOnAction(e -> collapseEditorToStrip());
        closeEditorDockButton.setOnAction(e -> hideEditorDock());
        editorEdgeStrip.setOnMouseClicked(this::onEditorEdgeStripClicked);
        centerSplit.widthProperty().addListener((obs, old, width) -> {
            if (editorLayout == EditorLayout.STRIP && width.doubleValue() > 0) {
                Platform.runLater(this::applyStripDivider);
            }
        });
        fileTreeRefresher = new WorkspaceFileTreeRefresher(
                fileTree,
                initialWorkspace,
                this::openFileInEditor,
                () -> filePanelVisible && activeWorkspace != null
        );
        refreshFileTreeButton.setOnAction(e -> fileTreeRefresher.refresh());
        fileTreeRefresher.setOnAfterRefresh(() -> Platform.runLater(this::reloadOpenEditorFilesFromDisk));
        closeFilePanelButton.setOnAction(e -> hideFilePanel());
        toggleFilePanelButton.setOnAction(e -> {
            if (filePanelVisible) {
                hideFilePanel();
            } else if (activeWorkspace != null) {
                showFilePanel(activeWorkspace);
            }
        });
        updateFilePanelToggle();
        hubButton.setOnAction(e -> sessionManager.appendSystemLine("请在「AiCode — 模型配置」主窗口中管理模型。"));

        populateModels();
        selectInitialModel();
        sessionManager.addInitialWorkspace(initialWorkspace);
    }

    private void onEditorEdgeStripClicked(MouseEvent event) {
        if (editorTabs != null && editorTabs.hasOpenTabs()) {
            showEditorExpanded();
        }
    }

    private void onWorkspaceActivated(Path workspace) {
        if (workspace.equals(activeWorkspace) && editorTabs != null) {
            if (!filePanelVisible) {
                showFilePanel(workspace);
            }
            return;
        }
        activeWorkspace = workspace;
        openFilePanel(workspace);
    }

    private void openFilePanel(Path workspace) {
        showFilePanel(workspace);
        filePanelTitle.setText(WorkingDirectory.displayName(workspace) + " · 双击打开");

        if (editorWorkspace == null || !workspace.equals(editorWorkspace)) {
            editorWorkspace = workspace;
            Platform.runLater(() -> initEditorTabs(workspace));
        }

        fileTreeRefresher.setWorkspace(workspace);
        WorkspaceFileTree.loadAsync(fileTree, workspace, this::openFileInEditor);
        if (filePanelVisible) {
            fileTreeRefresher.startAutoRefresh();
        }
    }

    private void initEditorTabs(Path workspace) {
        editorTabs = new EditorTabManager(workspaceGuard(workspace));
        editorTabs.setDialogOwner(() -> {
            if (sendButton.getScene() != null && sendButton.getScene().getWindow() instanceof Stage stage) {
                return stage;
            }
            return null;
        });
        editorTabs.setOnStatus(msg -> footerLabel.setText(msg));
        editorTabs.setOnDirtyChanged(this::updateSaveButtonDirtyState);
        editorTabs.setOnAllTabsClosed(this::hideEditorDock);
        VBox.setVgrow(editorTabs, Priority.ALWAYS);
        agentEditorHost.getChildren().setAll(editorTabs);
        hideEditorDock();

        composerInput.bindWorkspace(workspace);
        composerInput.setActiveFileSupplier(() -> editorTabs.activeFile().orElse(null));
        composerInput.setEditorPaneSupplier(() -> editorTabs.editorPane());
        composerInput.setFileLoader(this::loadFileForContext);
    }

    private void openFileInEditor(Path path) {
        try {
            EditorFileContent content = EditorFileLoader.load(path);
            Platform.runLater(() -> openFileWhenReady(path, content, 0));
        } catch (IOException e) {
            sessionManager.appendSystemLine("无法打开文件: " + e.getMessage());
        }
    }

    private void openFileWhenReady(Path path, EditorFileContent content, int attempt) {
        if (editorTabs == null) {
            if (attempt < 20) {
                Platform.runLater(() -> openFileWhenReady(path, content, attempt + 1));
            }
            return;
        }
        editorTabs.openFile(path, content);
        showEditorExpanded();
    }

    private void showFilePanel(Path workspace) {
        activeWorkspace = workspace;
        filePanel.setVisible(true);
        filePanel.setManaged(true);
        filePanelVisible = true;
        updateFilePanelToggle();
        if (fileTreeRefresher != null) {
            fileTreeRefresher.setWorkspace(workspace);
            fileTreeRefresher.startAutoRefresh();
        }
    }

    private void hideFilePanel() {
        filePanel.setVisible(false);
        filePanel.setManaged(false);
        filePanelVisible = false;
        updateFilePanelToggle();
        if (fileTreeRefresher != null) {
            fileTreeRefresher.stopAutoRefresh();
        }
    }

    private void showEditorExpanded() {
        if (editorTabs == null || !editorTabs.hasOpenTabs()) {
            return;
        }
        editorSlot.setVisible(true);
        editorSlot.setManaged(true);
        editorDock.setVisible(true);
        editorDock.setManaged(true);
        editorEdgeStrip.setVisible(false);
        editorEdgeStrip.setManaged(false);
        StackPane.setAlignment(editorDock, javafx.geometry.Pos.CENTER);
        centerSplit.setDividerPositions(0.0);
        editorLayout = EditorLayout.EXPANDED;
    }

    private void collapseEditorToStrip() {
        if (editorLayout != EditorLayout.EXPANDED || editorTabs == null || !editorTabs.hasOpenTabs()) {
            return;
        }
        editorSlot.setVisible(true);
        editorSlot.setManaged(true);
        editorDock.setVisible(false);
        editorDock.setManaged(false);
        editorEdgeStrip.setVisible(true);
        editorEdgeStrip.setManaged(true);
        applyStripDivider();
        editorLayout = EditorLayout.STRIP;
    }

    private void hideEditorDock() {
        editorDock.setVisible(false);
        editorDock.setManaged(false);
        editorEdgeStrip.setVisible(false);
        editorEdgeStrip.setManaged(false);
        editorSlot.setVisible(false);
        editorSlot.setManaged(false);
        centerSplit.setDividerPositions(1.0);
        editorLayout = EditorLayout.HIDDEN;
    }

    private void applyStripDivider() {
        double total = centerSplit.getWidth();
        if (total <= EDGE_STRIP_WIDTH * 2) {
            centerSplit.setDividerPositions(0.985);
            return;
        }
        double position = 1.0 - (EDGE_STRIP_WIDTH / total);
        centerSplit.setDividerPositions(Math.max(0.0, Math.min(1.0, position)));
    }

    private void updateFilePanelToggle() {
        if (toggleFilePanelButton == null) {
            return;
        }
        toggleFilePanelButton.setText(filePanelVisible ? "隐藏文件" : "文件");
    }

    private void loadFileForContext(Path path, java.util.function.Consumer<String> callback) {
        if (editorTabs != null && editorTabs.activeFile().map(path::equals).orElse(false)) {
            editorTabs.editorPane().getContentAsync()
                    .thenAccept(content -> Platform.runLater(() -> callback.accept(content)));
            return;
        }
        Thread.ofVirtual().name("agent-context-file-load").start(() -> {
            try {
                EditorFileContent loaded = EditorFileLoader.load(path);
                String content = switch (loaded.mode()) {
                    case TEXT, HEX -> loaded.text();
                    default -> "";
                };
                Platform.runLater(() -> callback.accept(content));
            } catch (IOException e) {
                Platform.runLater(() -> callback.accept(""));
            }
        });
    }

    private WorkspaceGuard workspaceGuard(Path workspace) {
        return new WorkspaceGuard(
                workspace,
                new com.aicode.agent.Safety.FileSystemSandbox(
                        com.aicode.agent.SkillContext.toolSandboxRoots(workspace)
                )
        );
    }

    private void populateModels() {
        ModelRegistry registry = windowManager.modelRegistry();
        modelBox.setCellFactory(cb -> createModelCell());
        modelBox.setButtonCell(createModelCell());
        modelBox.getItems().setAll(registry.models());
        modelBox.valueProperty().addListener((obs, old, model) -> {
            if (sessionManager != null) {
                sessionManager.applyModel(model);
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
            sessionManager.applyModel(null);
            return;
        }
        ModelProfile initial = registry.findById(initialModelId)
                .orElseGet(registry::defaultModel);
        if (registry.findById(initial.id()).isPresent()) {
            modelBox.setValue(initial);
        } else {
            modelBox.getSelectionModel().selectFirst();
        }
        sessionManager.applyModel(modelBox.getValue());
    }

    private void appendChat(String text) {
        Platform.runLater(() -> chatView.appendStandaloneNotice(text.strip()));
    }

    private void reloadEditedFile(Path path) {
        if (path == null || editorTabs == null) {
            return;
        }
        Thread.ofVirtual().name("reload-edited-file").start(() -> {
            try {
                if (!Files.isRegularFile(path)) {
                    Platform.runLater(() -> editorTabs.closeFile(path));
                    return;
                }
                EditorFileContent content = EditorFileLoader.load(path);
                Platform.runLater(() -> editorTabs.reloadFile(path, content));
            } catch (IOException ignored) {
                // ignore reload errors
            }
        });
    }

    private void reloadOpenEditorFilesFromDisk() {
        if (editorTabs != null) {
            editorTabs.reloadAllOpenFilesFromDisk();
        }
    }

    private void updateSaveButtonDirtyState(boolean dirty) {
        Platform.runLater(() -> {
            saveFileButton.setText(dirty ? "保存 *" : "保存");
            if (dirty) {
                if (!saveFileButton.getStyleClass().contains("agent-editor-dock-action-dirty")) {
                    saveFileButton.getStyleClass().add("agent-editor-dock-action-dirty");
                }
            } else {
                saveFileButton.getStyleClass().remove("agent-editor-dock-action-dirty");
            }
        });
    }
}

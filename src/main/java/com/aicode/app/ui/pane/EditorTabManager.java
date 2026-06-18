package com.aicode.app.ui.pane;

import com.aicode.app.application.WorkspaceGuard;
import com.aicode.app.config.WorkingDirectory;
import com.aicode.app.ui.dialog.ExternalFileChangeDialog;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Multi-tab file editor backed by a single Monaco {@link EditorPane}. */
public final class EditorTabManager extends VBox {
    private final TabPane tabPane = new TabPane();
    private final EditorPane editorPane = new EditorPane();
    private final Map<Path, EditorTab> tabs = new LinkedHashMap<>();
    private final Set<Path> pendingExternalPrompts = ConcurrentHashMap.newKeySet();
    private final OpenFileWatcher fileWatcher;
    private final WorkspaceGuard guard;
    private Supplier<Stage> dialogOwner = () -> null;
    private Consumer<Path> onActiveFileChanged;
    private Consumer<String> onStatus;
    private Consumer<Boolean> onDirtyChanged;
    private Runnable onAllTabsClosed;
    private EditorTab activeTab;

    public EditorTabManager(WorkspaceGuard guard) {
        this.guard = guard;
        this.fileWatcher = new OpenFileWatcher(new OpenFileWatcher.Listener() {
            @Override
            public void onExternalChange(Path path, String diskContent) {
                handleExternalDiskChange(path, diskContent);
            }

            @Override
            public void onExternalDelete(Path path) {
                Platform.runLater(() -> closeFile(path));
            }
        });
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.getStyleClass().add("editor-tabs");
        tabPane.setMinHeight(32);
        tabPane.setPrefHeight(32);
        tabPane.setMaxHeight(Region.USE_PREF_SIZE);
        tabPane.setTabMinWidth(96);
        VBox.setVgrow(editorPane, Priority.ALWAYS);
        getChildren().addAll(tabPane, editorPane);

        editorPane.setOnDirty(this::markActiveDirty);
        editorPane.startDirtyPolling();
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (oldTab != null) {
                cacheEditorContent(tabFor(oldTab));
            }
            if (newTab == null) {
                activeTab = null;
                return;
            }
            activeTab = tabFor(newTab);
            if (activeTab != null) {
                editorPane.openFile(activeTab.content(), activeTab.language());
                notifyActive(activeTab.path());
                notifyDirtyChanged(activeTab.dirty());
            }
        });
        fileWatcher.start();
    }

    public void setDialogOwner(Supplier<Stage> dialogOwner) {
        this.dialogOwner = dialogOwner != null ? dialogOwner : () -> null;
    }

    public void setOnActiveFileChanged(Consumer<Path> onActiveFileChanged) {
        this.onActiveFileChanged = onActiveFileChanged;
    }

    public void setOnStatus(Consumer<String> onStatus) {
        this.onStatus = onStatus;
    }

    public void setOnDirtyChanged(Consumer<Boolean> onDirtyChanged) {
        this.onDirtyChanged = onDirtyChanged;
    }

    public void setOnAllTabsClosed(Runnable onAllTabsClosed) {
        this.onAllTabsClosed = onAllTabsClosed;
    }

    public void dispose() {
        editorPane.stopDirtyPolling();
        fileWatcher.close();
    }

    public boolean hasOpenTabs() {
        return !tabs.isEmpty();
    }

    public Optional<Path> activeFile() {
        return activeTab != null ? Optional.of(activeTab.path()) : Optional.empty();
    }

    public void openFile(Path path, String content) {
        Path key = normalize(path);
        String blocked = guard.validate(key.toString());
        if (blocked != null) {
            status("拒绝打开: " + blocked);
            return;
        }
        EditorTab existing = tabs.get(key);
        if (existing != null) {
            if (!existing.dirty()) {
                applyContent(existing, content, true);
            }
            tabPane.getSelectionModel().select(existing.tab());
            fileWatcher.refreshBaseline(key);
            return;
        }
        EditorTab editorTab = new EditorTab(key, content);
        Tab tab = new Tab(displayName(key));
        tab.setUserData(editorTab);
        tab.setClosable(true);
        tab.setOnCloseRequest(e -> {
            cacheEditorContent(editorTab);
            closeTab(key, editorTab);
        });
        editorTab.bindTab(tab);
        tabs.put(key, editorTab);
        refreshTabTitle(editorTab);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        fileWatcher.track(key);
        status("已打开: " + key);
    }

    public void saveActiveFile() {
        if (activeTab == null) {
            return;
        }
        editorPane.getContentAsync().thenAccept(content -> Platform.runLater(() -> {
            try {
                String blocked = guard.validate(activeTab.path().toString());
                if (blocked != null) {
                    status("拒绝保存: " + blocked);
                    return;
                }
                Files.writeString(activeTab.path(), content);
                applyContent(activeTab, content, true);
                fileWatcher.refreshBaseline(activeTab.path());
                status("已保存: " + activeTab.path());
            } catch (IOException e) {
                status("保存失败: " + e.getMessage());
            }
        }));
    }

    public EditorPane editorPane() {
        return editorPane;
    }

    /** Reload from disk when the tab has no unsaved local edits (e.g. agent wrote the file). */
    public void reloadFile(Path path, String content) {
        Path key = normalize(path);
        EditorTab tab = tabs.get(key);
        if (tab != null) {
            if (tab.dirty()) {
                status("未刷新 (有未保存修改): " + key);
                return;
            }
            applyContent(tab, content, true);
            fileWatcher.refreshBaseline(key);
            status("已刷新: " + key);
            return;
        }
        openFile(path, content);
    }

    /** Reload every open tab from disk when it has no unsaved local edits. */
    public void reloadAllOpenFilesFromDisk() {
        for (Path path : List.copyOf(tabs.keySet())) {
            EditorTab tab = tabs.get(path);
            if (tab == null || tab.dirty()) {
                continue;
            }
            try {
                if (!Files.isRegularFile(path)) {
                    closeFile(path);
                    continue;
                }
                reloadFile(path, Files.readString(path));
            } catch (IOException ignored) {
                // skip unreadable file
            }
        }
    }

    public void closeFile(Path path) {
        Path key = normalize(path);
        EditorTab tab = tabs.remove(key);
        if (tab != null) {
            tabPane.getTabs().remove(tab.tab());
            fileWatcher.untrack(key);
            pendingExternalPrompts.remove(key);
            if (activeTab == tab) {
                activeTab = null;
            }
            if (tabs.isEmpty()) {
                notifyAllTabsClosed();
            }
        }
    }

    private void closeTab(Path key, EditorTab editorTab) {
        tabs.remove(key);
        fileWatcher.untrack(key);
        pendingExternalPrompts.remove(key);
        if (activeTab == editorTab) {
            activeTab = null;
        }
        if (tabs.isEmpty()) {
            notifyAllTabsClosed();
        }
    }

    private void handleExternalDiskChange(Path path, String diskContent) {
        Path key = normalize(path);
        if (pendingExternalPrompts.contains(key)) {
            return;
        }
        EditorTab tab = tabs.get(key);
        if (tab == null) {
            fileWatcher.untrack(key);
            return;
        }
        fileWatcher.acknowledgeDiskRevision(key);
        if (!tab.dirty()) {
            applyContent(tab, diskContent, true);
            status("已同步外部修改: " + key);
            return;
        }
        pendingExternalPrompts.add(key);
        Stage owner = dialogOwner.get();
        ExternalFileChangeDialog.show(owner, key, reload -> {
            pendingExternalPrompts.remove(key);
            EditorTab current = tabs.get(key);
            if (current == null) {
                return;
            }
            if (reload) {
                applyContent(current, diskContent, true);
                status("已重新加载: " + key);
            } else {
                status("保留本地编辑: " + key);
            }
        });
    }

    private void applyContent(EditorTab tab, String content, boolean markClean) {
        tab.setContent(content);
        if (markClean) {
            setTabDirty(tab, false);
        }
        if (activeTab == tab) {
            editorPane.openFile(content, tab.language());
            if (markClean) {
                editorPane.markClean();
            }
        } else if (markClean) {
            refreshTabTitle(tab);
        }
    }

    private void cacheEditorContent(EditorTab tab) {
        if (tab == null) {
            return;
        }
        editorPane.getContentAsync().thenAccept(content -> Platform.runLater(() -> tab.setContent(content)));
    }

    private void markActiveDirty() {
        if (activeTab != null) {
            setTabDirty(activeTab, true);
        }
    }

    private void setTabDirty(EditorTab tab, boolean dirty) {
        if (dirty) {
            tab.markDirty();
        } else {
            tab.markClean();
        }
        refreshTabTitle(tab);
        if (activeTab == tab) {
            notifyDirtyChanged(dirty);
        }
    }

    private void refreshTabTitle(EditorTab editorTab) {
        Tab tab = editorTab.tab();
        String name = displayName(editorTab.path());
        tab.setText(name + (editorTab.dirty() ? " *" : ""));
        if (editorTab.dirty()) {
            if (!tab.getStyleClass().contains("tab-dirty")) {
                tab.getStyleClass().add("tab-dirty");
            }
        } else {
            tab.getStyleClass().remove("tab-dirty");
        }
    }

    private void notifyDirtyChanged(boolean dirty) {
        if (onDirtyChanged != null) {
            onDirtyChanged.accept(dirty);
        }
    }

    private EditorTab tabFor(Tab tab) {
        return tab != null ? (EditorTab) tab.getUserData() : null;
    }

    private void notifyActive(Path path) {
        if (onActiveFileChanged != null) {
            onActiveFileChanged.accept(path);
        }
    }

    private void status(String message) {
        if (onStatus != null) {
            onStatus.accept(message);
        }
    }

    private void notifyAllTabsClosed() {
        if (onAllTabsClosed != null) {
            onAllTabsClosed.run();
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static String displayName(Path path) {
        return WorkingDirectory.displayName(path);
    }

    private static final class EditorTab {
        private final Path path;
        private final String language;
        private Tab tab;
        private String content;
        private boolean dirty;

        private EditorTab(Path path, String content) {
            this.path = path;
            this.language = EditorLanguage.detect(path);
            this.content = content;
        }

        Path path() {
            return path;
        }

        String language() {
            return language;
        }

        String content() {
            return content;
        }

        void setContent(String content) {
            this.content = content;
        }

        Tab tab() {
            return tab;
        }

        boolean dirty() {
            return dirty;
        }

        void bindTab(Tab tab) {
            this.tab = tab;
        }

        void markDirty() {
            dirty = true;
        }

        void markClean() {
            dirty = false;
        }
    }
}

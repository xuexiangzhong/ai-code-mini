package com.aicode.app.ui.pane;

import com.aicode.app.application.WorkspaceGuard;
import com.aicode.app.config.WorkingDirectory;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Multi-tab file editor backed by a single Monaco {@link EditorPane}. */
public final class EditorTabManager extends VBox {
    private final TabPane tabPane = new TabPane();
    private final EditorPane editorPane = new EditorPane();
    private final Map<Path, EditorTab> tabs = new LinkedHashMap<>();
    private final WorkspaceGuard guard;
    private Consumer<Path> onActiveFileChanged;
    private Consumer<String> onStatus;
    private Runnable onAllTabsClosed;
    private EditorTab activeTab;

    public EditorTabManager(WorkspaceGuard guard) {
        this.guard = guard;
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.getStyleClass().add("editor-tabs");
        tabPane.setMinHeight(36);
        tabPane.setPrefHeight(36);
        tabPane.setMaxHeight(Region.USE_PREF_SIZE);
        tabPane.setTabMinWidth(96);
        VBox.setVgrow(editorPane, Priority.ALWAYS);
        getChildren().addAll(tabPane, editorPane);

        editorPane.setOnDirty(this::markActiveDirty);
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
            }
        });
    }

    public void setOnActiveFileChanged(Consumer<Path> onActiveFileChanged) {
        this.onActiveFileChanged = onActiveFileChanged;
    }

    public void setOnStatus(Consumer<String> onStatus) {
        this.onStatus = onStatus;
    }

    public void setOnAllTabsClosed(Runnable onAllTabsClosed) {
        this.onAllTabsClosed = onAllTabsClosed;
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
            tabPane.getSelectionModel().select(existing.tab());
            return;
        }
        EditorTab editorTab = new EditorTab(key, content);
        Tab tab = new Tab(displayName(key));
        tab.setUserData(editorTab);
        tab.setClosable(true);
        tab.setOnCloseRequest(e -> {
            cacheEditorContent(editorTab);
            tabs.remove(key);
            if (activeTab == editorTab) {
                activeTab = null;
            }
            if (tabs.isEmpty()) {
                notifyAllTabsClosed();
            }
        });
        editorTab.bindTab(tab);
        tabs.put(key, editorTab);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
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
                activeTab.setContent(content);
                activeTab.markClean();
                editorPane.markClean();
                refreshTabTitle(activeTab);
                status("已保存: " + activeTab.path());
            } catch (IOException e) {
                status("保存失败: " + e.getMessage());
            }
        }));
    }

    public EditorPane editorPane() {
        return editorPane;
    }

    private void cacheEditorContent(EditorTab tab) {
        if (tab == null) {
            return;
        }
        editorPane.getContentAsync().thenAccept(content -> Platform.runLater(() -> tab.setContent(content)));
    }

    private void markActiveDirty() {
        if (activeTab != null) {
            activeTab.markDirty();
            refreshTabTitle(activeTab);
        }
    }

    private void refreshTabTitle(EditorTab editorTab) {
        editorTab.tab().setText((editorTab.dirty() ? "● " : "") + displayName(editorTab.path()));
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

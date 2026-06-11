package com.aicode.app.ui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.TreeView;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Manual and periodic refresh for {@link WorkspaceFileTree} with lightweight change detection. */
public final class WorkspaceFileTreeRefresher {
    private static final Duration DEFAULT_INTERVAL = Duration.seconds(4);

    private final TreeView<String> tree;
    private Path workspace;
    private final Consumer<Path> onFileDoubleClicked;
    private final BooleanSupplier activeWhen;

    private Map<Path, DirectorySnapshot.Entry> lastSnapshot = Map.of();
    private Timeline autoRefreshTimeline;
    private volatile boolean refreshInProgress;

    public WorkspaceFileTreeRefresher(
            TreeView<String> tree,
            Path workspace,
            Consumer<Path> onFileDoubleClicked,
            BooleanSupplier activeWhen
    ) {
        this.tree = tree;
        this.workspace = workspace;
        this.onFileDoubleClicked = onFileDoubleClicked;
        this.activeWhen = activeWhen;
    }

    public void setWorkspace(Path workspace) {
        this.workspace = workspace;
        lastSnapshot = Map.of();
    }

    public void refresh() {
        if (workspace == null || refreshInProgress) {
            return;
        }
        refreshInProgress = true;
        WorkspaceFileTree.refreshAsync(tree, workspace, onFileDoubleClicked, () -> {
            refreshInProgress = false;
            lastSnapshot = DirectorySnapshot.capture(tree, workspace);
        });
    }

    public void startAutoRefresh() {
        startAutoRefresh(DEFAULT_INTERVAL);
    }

    public void startAutoRefresh(Duration interval) {
        stopAutoRefresh();
        autoRefreshTimeline = new Timeline(new KeyFrame(interval, event -> pollForChanges()));
        autoRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        autoRefreshTimeline.play();
    }

    public void stopAutoRefresh() {
        if (autoRefreshTimeline != null) {
            autoRefreshTimeline.stop();
            autoRefreshTimeline = null;
        }
    }

    public void dispose() {
        stopAutoRefresh();
    }

    private void pollForChanges() {
        if (workspace == null || refreshInProgress || !activeWhen.getAsBoolean()) {
            return;
        }
        Map<Path, DirectorySnapshot.Entry> current = DirectorySnapshot.capture(tree, workspace);
        if (lastSnapshot.isEmpty()) {
            lastSnapshot = current;
            return;
        }
        if (DirectorySnapshot.changed(lastSnapshot, current)) {
            refresh();
        }
    }
}

package com.aicode.app.ui;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Lightweight directory listing fingerprint for cheap change detection. */
final class DirectorySnapshot {
    record Entry(long count, long latestModMillis) {
        static Entry unreadable() {
            return new Entry(-1, -1);
        }

        boolean matches(Entry other) {
            return count == other.count && latestModMillis == other.latestModMillis;
        }
    }

    private DirectorySnapshot() {
    }

    static Entry of(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Entry.unreadable();
        }
        try (Stream<Path> paths = Files.list(dir)) {
            long count = 0;
            long latest = 0;
            for (Path path : (Iterable<Path>) paths::iterator) {
                count++;
                try {
                    long mod = Files.getLastModifiedTime(path).toMillis();
                    if (mod > latest) {
                        latest = mod;
                    }
                } catch (IOException ignored) {
                    // skip unreadable entry
                }
            }
            return new Entry(count, latest);
        } catch (IOException e) {
            return Entry.unreadable();
        }
    }

    static Map<Path, Entry> capture(TreeView<String> tree, Path workspace) {
        Map<Path, Entry> snapshots = new HashMap<>();
        TreeItem<String> root = tree.getRoot();
        if (root != null) {
            collectLoadedDirs(root, workspace, root, snapshots);
        }
        return Map.copyOf(snapshots);
    }

    static boolean changed(Map<Path, Entry> previous, Map<Path, Entry> current) {
        if (previous.size() != current.size()) {
            return true;
        }
        for (Map.Entry<Path, Entry> entry : current.entrySet()) {
            Entry old = previous.get(entry.getKey());
            if (old == null || !old.matches(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    /** Snapshot every directory node already materialized in the tree (not lazy placeholders). */
    private static void collectLoadedDirs(
            TreeItem<String> item,
            Path workspace,
            TreeItem<String> treeRoot,
            Map<Path, Entry> snapshots
    ) {
        Path resolved = WorkspaceFileTree.resolvePath(workspace, treeRoot, item);
        if (resolved != null && Files.isDirectory(resolved)) {
            snapshots.putIfAbsent(resolved, of(resolved));
        }
        for (TreeItem<String> child : item.getChildren()) {
            if ("…".equals(child.getValue())) {
                continue;
            }
            collectLoadedDirs(child, workspace, treeRoot, snapshots);
        }
    }
}

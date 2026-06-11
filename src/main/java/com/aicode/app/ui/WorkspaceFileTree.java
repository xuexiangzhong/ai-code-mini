package com.aicode.app.ui;

import com.aicode.app.config.WorkingDirectory;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** Lazy-loading workspace file tree for the Agent window explorer panel. */
public final class WorkspaceFileTree {
    private WorkspaceFileTree() {
    }

    public static void loadAsync(TreeView<String> tree, Path root) {
        loadAsync(tree, root, null);
    }

    public static void loadAsync(TreeView<String> tree, Path root, Consumer<Path> onFileDoubleClicked) {
        refreshAsync(tree, root, onFileDoubleClicked, null);
    }

    public static void refreshAsync(
            TreeView<String> tree,
            Path root,
            Consumer<Path> onFileDoubleClicked,
            Runnable onComplete
    ) {
        Set<String> expandedPaths = collectExpandedPaths(tree, root);
        String selectedRelative = selectedRelativePath(tree, root);

        tree.setRoot(new TreeItem<>("加载中…"));
        tree.setShowRoot(true);
        Thread.ofVirtual().name("agent-file-tree").start(() -> {
            TreeItem<String> rootItem = buildTreeRoot(root, expandedPaths);
            Platform.runLater(() -> {
                tree.setRoot(rootItem);
                bindDoubleClickHandler(tree, root, onFileDoubleClicked);
                restoreSelection(tree, root, selectedRelative);
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        });
    }

    public static Path resolvePath(Path root, TreeItem<String> treeRoot, TreeItem<String> item) {
        if (item == treeRoot) {
            return root;
        }
        String label = item.getValue();
        if (label == null || "…".equals(label) || label.startsWith("(无法读取") || "加载中…".equals(label)) {
            return null;
        }
        StringBuilder relative = new StringBuilder(label);
        TreeItem<String> parent = item.getParent();
        while (parent != null && parent != treeRoot) {
            relative.insert(0, parent.getValue() + "/");
            parent = parent.getParent();
        }
        return root.resolve(relative.toString());
    }

    private static void bindDoubleClickHandler(TreeView<String> tree, Path root, Consumer<Path> onFileDoubleClicked) {
        if (onFileDoubleClicked == null) {
            return;
        }
        tree.setOnMouseClicked(e -> {
            if (e.getClickCount() != 2) {
                return;
            }
            TreeItem<String> selected = tree.getSelectionModel().getSelectedItem();
            if (selected == null || selected.getValue() == null) {
                return;
            }
            Path resolved = resolvePath(root, tree.getRoot(), selected);
            if (resolved != null && Files.isRegularFile(resolved)) {
                onFileDoubleClicked.accept(resolved);
            }
        });
    }

    private static Set<String> collectExpandedPaths(TreeView<String> tree, Path root) {
        Set<String> expanded = new HashSet<>();
        TreeItem<String> treeRoot = tree.getRoot();
        if (treeRoot == null) {
            return expanded;
        }
        collectExpandedPaths(treeRoot, root, treeRoot, expanded);
        return expanded;
    }

    private static void collectExpandedPaths(
            TreeItem<String> item,
            Path root,
            TreeItem<String> treeRoot,
            Set<String> expanded
    ) {
        if (item != treeRoot && item.isExpanded()) {
            Path resolved = resolvePath(root, treeRoot, item);
            if (resolved != null) {
                expanded.add(relativePath(root, resolved));
            }
        }
        for (TreeItem<String> child : item.getChildren()) {
            collectExpandedPaths(child, root, treeRoot, expanded);
        }
    }

    private static String selectedRelativePath(TreeView<String> tree, Path root) {
        TreeItem<String> treeRoot = tree.getRoot();
        TreeItem<String> selected = tree.getSelectionModel().getSelectedItem();
        if (treeRoot == null || selected == null) {
            return null;
        }
        Path resolved = resolvePath(root, treeRoot, selected);
        return resolved == null ? null : relativePath(root, resolved);
    }

    private static void restoreSelection(TreeView<String> tree, Path root, String selectedRelative) {
        if (selectedRelative == null) {
            return;
        }
        TreeItem<String> match = findByRelativePath(tree.getRoot(), root, tree.getRoot(), selectedRelative);
        if (match != null) {
            tree.getSelectionModel().select(match);
        }
    }

    private static TreeItem<String> findByRelativePath(
            TreeItem<String> item,
            Path root,
            TreeItem<String> treeRoot,
            String relative
    ) {
        Path resolved = resolvePath(root, treeRoot, item);
        if (resolved != null && relativePath(root, resolved).equals(relative)) {
            return item;
        }
        for (TreeItem<String> child : item.getChildren()) {
            TreeItem<String> match = findByRelativePath(child, root, treeRoot, relative);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static String relativePath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static TreeItem<String> buildTreeRoot(Path root, Set<String> expandedPaths) {
        TreeItem<String> rootItem = new TreeItem<>(WorkingDirectory.displayName(root));
        rootItem.setExpanded(true);
        if (Files.isDirectory(root)) {
            loadDirectoryChildren(root, rootItem, root, expandedPaths);
        }
        return rootItem;
    }

    private static void loadDirectoryChildren(
            Path dir,
            TreeItem<String> parent,
            Path root,
            Set<String> expandedPaths
    ) {
        try (Stream<Path> paths = Files.list(dir)) {
            paths.sorted().limit(500).forEach(path -> parent.getChildren().add(createLazyNode(path, root, expandedPaths)));
        } catch (IOException e) {
            parent.getChildren().add(new TreeItem<>("(无法读取: " + e.getMessage() + ")"));
        }
    }

    private static TreeItem<String> createLazyNode(Path path, Path root, Set<String> expandedPaths) {
        TreeItem<String> item = new TreeItem<>(WorkingDirectory.displayName(path));
        if (!Files.isDirectory(path)) {
            return item;
        }

        String relative = relativePath(root, path);
        boolean shouldExpand = expandedPaths.contains(relative)
                || expandedPaths.stream().anyMatch(expanded -> expanded.startsWith(relative + "/"));
        if (shouldExpand) {
            loadDirectoryChildren(path, item, root, expandedPaths);
            item.setExpanded(true);
            return item;
        }

        item.getChildren().add(new TreeItem<>("…"));
        item.expandedProperty().addListener((obs, wasExpanded, expanded) -> {
            if (!expanded || item.getChildren().isEmpty() || !"…".equals(item.getChildren().get(0).getValue())) {
                return;
            }
            item.getChildren().clear();
            Thread.ofVirtual().name("agent-file-tree-expand").start(() -> {
                ArrayList<TreeItem<String>> children = new ArrayList<>();
                try (Stream<Path> paths = Files.list(path)) {
                    paths.sorted().limit(500).forEach(child -> children.add(createLazyNode(child, root, expandedPaths)));
                } catch (IOException e) {
                    children.add(new TreeItem<>("(无法读取)"));
                }
                Platform.runLater(() -> item.getChildren().setAll(children));
            });
        });
        return item;
    }
}

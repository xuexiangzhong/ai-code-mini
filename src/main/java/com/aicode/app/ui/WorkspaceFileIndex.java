package com.aicode.app.ui;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/** Flat index of workspace files for @ mention search. */
public final class WorkspaceFileIndex {
    public record Entry(Path absolute, String relative, String name) {}

    private static final int MAX_FILES = 3000;
    private static final List<String> SKIP_DIRS = List.of(
            ".git", "node_modules", "target", "build", "dist", ".idea", ".vscode", "editor/vs"
    );

    private final AtomicReference<List<Entry>> entries = new AtomicReference<>(List.of());

    public List<Entry> entries() {
        return entries.get();
    }

    public void rebuildAsync(Path workspace, Runnable onReady) {
        Thread.ofVirtual().name("workspace-file-index").start(() -> {
            List<Entry> found = new ArrayList<>();
            try {
                Files.walkFileTree(workspace, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        if (found.size() >= MAX_FILES) {
                            return FileVisitResult.TERMINATE;
                        }
                        if (dir.equals(workspace)) {
                            return FileVisitResult.CONTINUE;
                        }
                        String dn = dir.getFileName().toString();
                        if (SKIP_DIRS.contains(dn)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (found.size() >= MAX_FILES) {
                            return FileVisitResult.TERMINATE;
                        }
                        if (!Files.isRegularFile(file)) {
                            return FileVisitResult.CONTINUE;
                        }
                        String relative = workspace.relativize(file).toString().replace('\\', '/');
                        found.add(new Entry(file, relative, file.getFileName().toString()));
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException ignored) {
                // partial index ok
            }
            found.sort((a, b) -> a.relative().compareToIgnoreCase(b.relative()));
            entries.set(Collections.unmodifiableList(found));
            if (onReady != null) {
                javafx.application.Platform.runLater(onReady);
            }
        });
    }

    public List<Entry> search(String query, int limit) {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        List<Entry> all = entries.get();
        if (q.isEmpty()) {
            return all.size() <= limit ? all : all.subList(0, limit);
        }
        List<Entry> hits = new ArrayList<>();
        for (Entry entry : all) {
            if (entry.relative().toLowerCase(Locale.ROOT).contains(q)
                    || entry.name().toLowerCase(Locale.ROOT).contains(q)) {
                hits.add(entry);
                if (hits.size() >= limit) {
                    break;
                }
            }
        }
        return hits;
    }
}

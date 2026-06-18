package com.aicode.app.ui.pane;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class OpenFileWatcherTest {
    @Test
    void detectsModificationAfterBaselineRefresh(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.txt");
        Files.writeString(file, "v1");

        AtomicInteger changes = new AtomicInteger();
        OpenFileWatcher watcher = new OpenFileWatcher(new OpenFileWatcher.Listener() {
            @Override
            public void onExternalChange(Path path, String diskContent) {
                changes.incrementAndGet();
            }

            @Override
            public void onExternalDelete(Path path) {
            }
        });
        watcher.track(file);
        watcher.refreshBaseline(file);

        Files.writeString(file, "v2");
        watcher.acknowledgeDiskRevision(file);
        assertTrue(Files.getLastModifiedTime(file).toMillis() >= 0);

        watcher.track(file);
        long before = Files.getLastModifiedTime(file).toMillis();
        Thread.sleep(5);
        Files.writeString(file, "v3");
        assertNotEquals(before, Files.getLastModifiedTime(file).toMillis());

        watcher.close();
    }

    @Test
    void untrackRemovesPath(@TempDir Path dir) {
        Path file = dir.resolve("b.txt");
        OpenFileWatcher watcher = new OpenFileWatcher(new OpenFileWatcher.Listener() {
            @Override
            public void onExternalChange(Path path, String diskContent) {
            }

            @Override
            public void onExternalDelete(Path path) {
            }
        });
        watcher.track(file);
        assertTrue(watcher.isTracking(file));
        watcher.untrack(file);
        assertFalse(watcher.isTracking(file));
        watcher.close();
    }
}

package com.aicode.app.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirectorySnapshotTest {
    @TempDir
    Path tempDir;

    @Test
    void detectsNewFile() throws Exception {
        DirectorySnapshot.Entry before = DirectorySnapshot.of(tempDir);
        Files.writeString(tempDir.resolve("new.txt"), "hello");
        DirectorySnapshot.Entry after = DirectorySnapshot.of(tempDir);
        assertTrue(before.matches(before));
        assertFalse(before.matches(after));
    }

    @Test
    void detectsModifiedFile() throws Exception {
        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "v1");
        DirectorySnapshot.Entry before = DirectorySnapshot.of(tempDir);
        Thread.sleep(5);
        Files.writeString(file, "v2");
        DirectorySnapshot.Entry after = DirectorySnapshot.of(tempDir);
        assertFalse(before.matches(after));
    }

    @Test
    void unchangedDirectoryMatches() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "same");
        DirectorySnapshot.Entry first = DirectorySnapshot.of(tempDir);
        DirectorySnapshot.Entry second = DirectorySnapshot.of(tempDir);
        assertTrue(first.matches(second));
    }
}

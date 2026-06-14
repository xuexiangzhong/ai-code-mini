package com.aicode.agent;

import com.aicode.agent.tools.ListDirTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ListDirTest {
    @TempDir
    Path testDir;

    @Test
    void listsImmediateChildren() throws Exception {
        Files.writeString(testDir.resolve("a.txt"), "a");
        Files.createDirectories(testDir.resolve("sub"));
        Files.writeString(testDir.resolve("sub/b.txt"), "b");

        String result = ListDirTool.execute(new ListDirTool.Input(testDir.toString(), 1));
        assertTrue(result.contains("a.txt"));
        assertTrue(result.contains("sub/"));
        assertFalse(result.contains("b.txt"));
    }

    @Test
    void listsRecursivelyWithDepth() throws Exception {
        Files.createDirectories(testDir.resolve("sub"));
        Files.writeString(testDir.resolve("sub/b.txt"), "b");

        String result = ListDirTool.execute(new ListDirTool.Input(testDir.toString(), 2));
        assertTrue(result.contains("b.txt"));
    }

    @Test
    void missingPath() {
        String result = ListDirTool.execute(new ListDirTool.Input(testDir.resolve("nope").toString(), 1));
        assertTrue(result.contains("not found"));
    }
}

package com.aicode.agent;

import com.aicode.agent.tools.DeleteTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DeleteTest {
    @TempDir
    Path testDir;

    @Test
    void deletesFile() throws Exception {
        Path path = testDir.resolve("remove.txt");
        Files.writeString(path, "x");
        String result = DeleteTool.execute(new DeleteTool.Input(path.toString()));
        assertTrue(result.contains("Deleted"));
        assertFalse(Files.exists(path));
    }

    @Test
    void rejectsDirectory() throws Exception {
        Path dir = testDir.resolve("dir");
        Files.createDirectory(dir);
        String result = DeleteTool.execute(new DeleteTool.Input(dir.toString()));
        assertTrue(result.contains("directory"));
        assertTrue(Files.exists(dir));
    }

    @Test
    void missingFile() {
        String result = DeleteTool.execute(new DeleteTool.Input(testDir.resolve("nope.txt").toString()));
        assertTrue(result.contains("not found"));
    }
}

package com.aicode.agent;

import com.aicode.agent.tools.WriteTool;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WriteTest {
    @TempDir
    Path testDir;

    @Nested
    class TestWriteToolDefinition {
        @Test
        void hasCorrectName() {
            assertEquals("write_file", WriteTool.DEFINITION.name());
        }

        @Test
        void hasRequiredFields() {
            @SuppressWarnings("unchecked")
            var required = (java.util.List<String>) WriteTool.DEFINITION.inputSchema().get("required");
            assertTrue(required.contains("file_path"));
            assertTrue(required.contains("content"));
        }
    }

    @Nested
    class TestExecuteWriteTool {
        @Test
        void createNewFile() throws IOException {
            Path path = testDir.resolve("new.txt");
            String result = WriteTool.execute(new WriteTool.Input(path.toString(), "hello\nworld\n"));
            assertTrue(result.contains("Created"));
            assertTrue(result.contains("new.txt"));
            assertEquals("hello\nworld\n", Files.readString(path));
        }

        @Test
        void createParentDirectories() {
            Path path = testDir.resolve("deep/nested/file.txt");
            String result = WriteTool.execute(new WriteTool.Input(path.toString(), "nested content"));
            assertTrue(result.contains("Created"));
            assertTrue(Files.exists(path));
        }

        @Test
        void overwriteAndShowDiff() throws IOException {
            Path path = testDir.resolve("existing.txt");
            Files.writeString(path, "old line 1\nold line 2\n");
            String result = WriteTool.execute(
                    new WriteTool.Input(path.toString(), "new line 1\nold line 2\n"));
            assertTrue(result.contains("Updated"));
            assertTrue(result.contains("-old line 1"));
            assertTrue(result.contains("+new line 1"));
            assertEquals("new line 1\nold line 2\n", Files.readString(path));
        }

        @Test
        void noChanges() throws IOException {
            Path path = testDir.resolve("same.txt");
            Files.writeString(path, "same content\n");
            String result = WriteTool.execute(
                    new WriteTool.Input(path.toString(), "same content\n"));
            assertTrue(result.contains("(no changes)"));
        }

        @Test
        void reportsLineCount() {
            Path path = testDir.resolve("lines.txt");
            String result = WriteTool.execute(new WriteTool.Input(path.toString(), "a\nb\nc\n"));
            assertTrue(result.contains("4 lines"));
        }
    }
}

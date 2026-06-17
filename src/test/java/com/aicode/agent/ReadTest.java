package com.aicode.agent;

import com.aicode.agent.tools.ReadTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ReadTest {
    @TempDir
    Path testDir;

    Path sampleTxt;
    Path binaryBin;

    @BeforeEach
    void setUp() throws IOException {
        StringBuilder lines = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            lines.append("Line ").append(i + 1).append(": content");
            if (i < 19) {
                lines.append("\n");
            }
        }
        lines.append("\n");
        sampleTxt = testDir.resolve("sample.txt");
        Files.writeString(sampleTxt, lines.toString());

        byte[] buf = new byte[100];
        buf[0] = (byte) 0x89;
        buf[5] = 0x00;
        binaryBin = testDir.resolve("binary.bin");
        Files.write(binaryBin, buf);
    }

    @Nested
    class TestReadToolDefinition {
        @Test
        void hasCorrectName() {
            assertEquals("read_file", ReadTool.DEFINITION.name());
        }

        @Test
        void hasRequiredFields() {
            @SuppressWarnings("unchecked")
            var required = (java.util.List<String>) ReadTool.DEFINITION.inputSchema().get("required");
            assertTrue(required.contains("file_path"));
        }
    }

    @Nested
    class TestExecuteReadTool {
        @Test
        void readEntireFile() {
            String result = ReadTool.execute(new ReadTool.Input(sampleTxt.toString(), 1, null));
            assertTrue(result.contains("1\tLine 1: content"));
            assertTrue(result.contains("20\tLine 20: content"));
        }

        @Test
        void offset() {
            String result = ReadTool.execute(new ReadTool.Input(sampleTxt.toString(), 5, null));
            String[] lines = result.split("\n");
            assertTrue(lines[0].strip().startsWith("5\tLine 5: content"));
            assertTrue(result.contains("Line 20: content"));
        }

        @Test
        void limit() {
            String result = ReadTool.execute(new ReadTool.Input(sampleTxt.toString(), 1, 3));
            assertEquals(3, result.lines().filter(line -> !line.startsWith("…")).count());
            assertTrue(result.contains("Line 1: content"));
            assertTrue(result.contains("Line 3: content"));
        }

        @Test
        void offsetAndLimit() {
            String result = ReadTool.execute(new ReadTool.Input(sampleTxt.toString(), 10, 5));
            assertEquals(5, result.lines().filter(line -> !line.startsWith("…")).count());
            assertTrue(result.contains("Line 10: content"));
            assertTrue(result.contains("Line 14: content"));
        }

        @Test
        void fileNotFound() {
            String result = ReadTool.execute(new ReadTool.Input("/no/such/file.txt", 1, null));
            assertTrue(result.contains("Error: file not found"));
        }

        @Test
        void directoryNotFile() {
            String result = ReadTool.execute(new ReadTool.Input(testDir.toString(), 1, null));
            assertTrue(result.contains("Error: not a file"));
        }

        @Test
        void binaryFile() {
            String result = ReadTool.execute(new ReadTool.Input(binaryBin.toString(), 1, null));
            assertTrue(result.contains("Error: binary file detected"));
        }

        @Test
        void outOfRangeOffset() {
            String result = ReadTool.execute(new ReadTool.Input(sampleTxt.toString(), 999, null));
            assertTrue(result.toLowerCase().contains("empty"));
        }

        @Test
        void invalidOffset() {
            String result = ReadTool.execute(new ReadTool.Input(sampleTxt.toString(), 0, null));
            assertTrue(result.contains("Error: offset must be >= 1"));
        }

        @Test
        void defaultLimitTruncatesLargeFiles() throws IOException {
            StringBuilder lines = new StringBuilder();
            for (int i = 0; i < 300; i++) {
                lines.append("Line ").append(i + 1).append("\n");
            }
            Path large = testDir.resolve("large.txt");
            Files.writeString(large, lines.toString());

            String result = ReadTool.execute(new ReadTool.Input(large.toString(), 1, null));
            assertTrue(result.contains("Line 250"));
            assertFalse(result.contains("\t251\tLine 251"));
            assertTrue(result.contains("300 total"));
        }
    }
}

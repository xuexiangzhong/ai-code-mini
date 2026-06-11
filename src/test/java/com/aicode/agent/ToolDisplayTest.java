package com.aicode.agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.aicode.agent.Markdown.stripAnsi;
import static org.junit.jupiter.api.Assertions.*;

class ToolDisplayTest {
    @Nested
    class TestTruncate {
        @Test
        void shortText() {
            assertEquals("hello", ToolDisplay.truncate("hello", 10));
        }

        @Test
        void longText() {
            assertEquals("hello...", ToolDisplay.truncate("hello world", 8));
        }

        @Test
        void exactLength() {
            assertEquals("hello", ToolDisplay.truncate("hello", 5));
        }
    }

    @Nested
    class TestFormatParams {
        @Test
        void keyValuePairs() {
            String result = ToolDisplay.formatParams(Map.of("file_path", "test.txt", "limit", 10), 200);
            assertTrue(result.contains("file_path:"));
            assertTrue(result.contains("\"test.txt\""));
            assertTrue(result.contains("limit: 10"));
        }

        @Test
        void truncateLongStrings() {
            String result = ToolDisplay.formatParams(Map.of("content", "x".repeat(100)), 200);
            assertTrue(result.contains("..."));
        }

        @Test
        void emptyInput() {
            assertEquals("", ToolDisplay.formatParams(Map.of(), 200));
        }

        @Test
        void truncateOverall() {
            Map<String, Object> inp = new java.util.LinkedHashMap<>();
            for (int i = 0; i < 10; i++) {
                inp.put("key" + i, "value");
            }
            String result = ToolDisplay.formatParams(inp, 50);
            assertTrue(result.length() <= 50);
        }
    }

    @Nested
    class TestFormatToolCall {
        @Test
        void nameAndParams() {
            String result = stripAnsi(ToolDisplay.formatToolCall("read_file", Map.of("file_path", "test.txt")));
            assertTrue(result.contains("read_file"));
            assertTrue(result.contains("test.txt"));
        }

        @Test
        void emptyParams() {
            String result = stripAnsi(ToolDisplay.formatToolCall("list_tasks", Map.of()));
            assertTrue(result.contains("list_tasks"));
        }
    }

    @Nested
    class TestFormatDuration {
        @Test
        void milliseconds() {
            assertEquals("50ms", ToolDisplay.formatDuration(50));
            assertEquals("999ms", ToolDisplay.formatDuration(999));
        }

        @Test
        void seconds() {
            assertEquals("1.5s", ToolDisplay.formatDuration(1500));
            assertEquals("30.0s", ToolDisplay.formatDuration(30000));
        }

        @Test
        void minutes() {
            assertEquals("1.5m", ToolDisplay.formatDuration(90000));
        }
    }

    @Nested
    class TestFormatToolResult {
        @Test
        void shortResults() {
            String result = ToolDisplay.formatToolResult("line 1\nline 2", 5, 120);
            assertTrue(result.contains("line 1"));
            assertTrue(result.contains("line 2"));
        }

        @Test
        void collapseLongResults() {
            String lines = String.join("\n",
                    java.util.stream.IntStream.range(0, 20).mapToObj(i -> "line " + i).toList());
            String result = ToolDisplay.formatToolResult(lines, 5, 120);
            assertTrue(result.contains("line 0"));
            assertTrue(result.contains("line 4"));
            assertTrue(result.contains("15 more lines"));
        }

        @Test
        void truncateLongLines() {
            String longLine = "x".repeat(200);
            String result = ToolDisplay.formatToolResult(longLine, 5, 50);
            assertTrue(stripAnsi(result).length() < 200);
        }

        @Test
        void emptyResult() {
            assertEquals("", ToolDisplay.formatToolResult("", 5, 120));
        }
    }

    @Nested
    class TestFormatToolCycle {
        @Test
        void combinesAll() {
            String result = stripAnsi(ToolDisplay.formatToolCycle(
                    "read_file", Map.of("file_path", "test.txt"), "file contents", 150));
            assertTrue(result.contains("read_file"));
            assertTrue(result.contains("test.txt"));
            assertTrue(result.contains("150ms"));
            assertTrue(result.contains("file contents"));
        }
    }

    @Nested
    class TestSpinner {
        @Test
        void storeMessage() {
            ToolDisplay.Spinner spinner = new ToolDisplay.Spinner("Loading...");
            assertEquals("Loading...", spinner.message());
        }

        @Test
        void updateMessage() {
            ToolDisplay.Spinner spinner = new ToolDisplay.Spinner("Loading...");
            spinner.update("Still loading...");
            assertEquals("Still loading...", spinner.message());
        }

        @Test
        void currentFrame() {
            ToolDisplay.Spinner spinner = new ToolDisplay.Spinner("test");
            assertEquals("⠋", spinner.currentFrame());
        }

        @Test
        void runningState() {
            ToolDisplay.Spinner spinner = new ToolDisplay.Spinner("test");
            assertFalse(spinner.isRunning());
        }

        @Test
        void startAndStop() throws InterruptedException {
            ToolDisplay.Spinner spinner = new ToolDisplay.Spinner("test");
            spinner.start();
            assertTrue(spinner.isRunning());
            Thread.sleep(100);
            spinner.stop();
            assertFalse(spinner.isRunning());
        }

        @Test
        void succeedMessage() {
            ToolDisplay.Spinner spinner = new ToolDisplay.Spinner("test");
            assertDoesNotThrow(() -> spinner.succeed("Done!"));
        }

        @Test
        void failMessage() {
            ToolDisplay.Spinner spinner = new ToolDisplay.Spinner("test");
            assertDoesNotThrow(() -> spinner.fail("Failed!"));
        }
    }
}

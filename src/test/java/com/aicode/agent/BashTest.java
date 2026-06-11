package com.aicode.agent;

import com.aicode.agent.tools.BashTool;
import com.aicode.agent.tools.ShellRunner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BashTest {
    @Nested
    class TestBashToolDefinition {
        @Test
        void hasCorrectName() {
            assertEquals("bash", BashTool.DEFINITION.name());
        }

        @Test
        void hasRequiredFields() {
            @SuppressWarnings("unchecked")
            var required = (java.util.List<String>) BashTool.DEFINITION.inputSchema().get("required");
            assertTrue(required.contains("command"));
        }

        @Test
        void descriptionMentionsCrossPlatformShell() {
            String description = BashTool.DEFINITION.description();
            assertTrue(description.contains("PowerShell") || description.contains("bash"));
        }
    }

    @Nested
    class TestExecuteBashTool {
        @Test
        void simpleCommand() {
            String result = BashTool.execute(new BashTool.Input("echo hello", 30)).join();
            assertEquals("hello", result.strip());
        }

        @Test
        void multiLineOutput() {
            String result = BashTool.execute(
                    new BashTool.Input(ShellRunner.multiLineEchoCommand(), 30)).join();
            assertTrue(result.contains("line1"));
            assertTrue(result.contains("line2"));
        }

        @Test
        void captureStderr() {
            String result = BashTool.execute(
                    new BashTool.Input(ShellRunner.stderrCommand("error"), 30)).join();
            assertTrue(result.contains("STDERR:"));
            assertTrue(result.contains("error"));
        }

        @Test
        void nonZeroExitCode() {
            String result = BashTool.execute(new BashTool.Input("exit 42", 30)).join();
            assertTrue(result.contains("Exit code: 42"));
        }

        @Test
        void commandNotFound() {
            String result = BashTool.execute(
                    new BashTool.Input("nonexistent_cmd_xyz", 30)).join();
            assertTrue(result.toLowerCase().contains("not found")
                    || result.contains("Exit code:")
                    || result.contains("STDERR:"));
        }

        @Test
        void timeout() {
            String result = BashTool.execute(
                    new BashTool.Input(ShellRunner.sleepCommand(60), 0.5)).join();
            assertTrue(result.contains("timed out"));
        }

        @Test
        void noOutput() {
            String result = BashTool.execute(new BashTool.Input(ShellRunner.noopCommand(), 30)).join();
            assertEquals("(no output)", result);
        }

        @Test
        void pipeCommand() {
            String result = BashTool.execute(
                    new BashTool.Input(ShellRunner.pipeToUpperCommand(), 30)).join();
            assertEquals(ShellRunner.expectedPipeToUpperResult(), result.strip());
        }
    }
}

package com.aicode.agent;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ReplTest {
    @Nested
    class TestNormalizeInput {
        @Test
        void trimWhitespace() {
            assertEquals("hello", Repl.normalizeInput("  hello  "));
        }

        @Test
        void emptyString() {
            assertEquals("", Repl.normalizeInput(""));
            assertEquals("", Repl.normalizeInput("   "));
        }
    }

    @Nested
    class TestParseCommand {
        @Test
        void extractCommandName() {
            assertEquals("/help", Repl.parseCommand("/help"));
            assertEquals("/help", Repl.parseCommand("/help arg"));
        }

        @Test
        void lowercase() {
            assertEquals("/help", Repl.parseCommand("/HELP"));
        }

        @Test
        void plainText() {
            assertEquals("hello", Repl.parseCommand("hello world"));
        }
    }

    @Nested
    class TestIsMultiLine {
        @Test
        void detectNewlines() {
            assertTrue(Repl.isMultiLine("line1\nline2"));
        }

        @Test
        void singleLine() {
            assertFalse(Repl.isMultiLine("single line"));
        }
    }

    @Nested
    class TestFormatHelp {
        @Test
        void listCommands() {
            var commands = List.of(
                    new Repl.Command("/help", "Show help", () -> ""),
                    new Repl.Command("/clear", "Clear screen", () -> null)
            );
            String helpText = Repl.formatHelp(commands, List.of("/exit"));
            assertTrue(helpText.contains("Available commands:"));
            assertTrue(helpText.contains("/help"));
            assertTrue(helpText.contains("/clear"));
            assertTrue(helpText.contains("/exit"));
        }
    }

    @Nested
    class TestRepl {
        @Test
        void blankInput() {
            Repl.ReplHandle repl = Repl.createRepl();
            assertEquals("", repl.processInput("").join());
            assertEquals("", repl.processInput("   ").join());
        }

        @Test
        void exitKeywords() {
            Repl.ReplHandle repl = Repl.createRepl();
            assertNull(repl.processInput("/exit").join());
            assertNull(repl.processInput("/quit").join());
        }

        @Test
        void customExitKeywords() {
            Repl.ReplHandle repl = Repl.createRepl(
                    new Repl.ReplConfig("> ", List.of("/bye"), List.of(), null));
            assertNull(repl.processInput("/bye").join());
            assertNotNull(repl.processInput("/exit").join());
        }

        @Test
        void helpCommand() {
            Repl.ReplHandle repl = Repl.createRepl();
            String result = repl.processInput("/help").join();
            assertNotNull(result);
            assertTrue(result.contains("Available commands:"));
            assertTrue(result.contains("/help"));
        }

        @Test
        void delegateToOnInput() {
            Repl.ReplHandle repl = Repl.createRepl(new Repl.ReplConfig(
                    "> ", List.of("/exit", "/quit"), List.of(),
                    text -> CompletableFuture.completedFuture("Echo: " + text)));
            String result = repl.processInput("hello").join();
            assertEquals("Echo: hello", result);
        }

        @Test
        void unknownCommandWithoutHandler() {
            Repl.ReplHandle repl = Repl.createRepl();
            String result = repl.processInput("unknown").join();
            assertNotNull(result);
            assertTrue(result.contains("Unknown command"));
        }

        @Test
        void customCommands() {
            Repl.Command custom = new Repl.Command("/test", "Test command", () -> "test result");
            Repl.ReplHandle repl = Repl.createRepl(
                    new Repl.ReplConfig("> ", List.of("/exit", "/quit"), List.of(custom), null));
            assertEquals("test result", repl.processInput("/test").join());
        }

        @Test
        void customCommandsInHelp() {
            Repl.Command custom = new Repl.Command("/test", "Test command", () -> "ok");
            Repl.ReplHandle repl = Repl.createRepl(
                    new Repl.ReplConfig("> ", List.of("/exit", "/quit"), List.of(custom), null));
            String helpText = repl.processInput("/help").join();
            assertNotNull(helpText);
            assertTrue(helpText.contains("/test"));
            assertTrue(helpText.contains("Test command"));
        }

        @Test
        void caseInsensitive() {
            Repl.ReplHandle repl = Repl.createRepl();
            String result = repl.processInput("/HELP").join();
            assertNotNull(result);
            assertTrue(result.contains("Available commands:"));
            assertNull(repl.processInput("/EXIT").join());
        }
    }
}

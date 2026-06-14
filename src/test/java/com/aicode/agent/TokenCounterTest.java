package com.aicode.agent;

import com.aicode.app.config.AppConfig;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.Tool;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TokenCounterTest {
    @Nested
    class TestEstimateTokens {
        @Test
        void emptyString() {
            assertEquals(0, TokenCounter.estimateTokens(""));
        }

        @Test
        void englishText() {
            assertEquals(3, TokenCounter.estimateTokens("Hello World"));
        }

        @Test
        void cjkText() {
            assertEquals(2, TokenCounter.estimateTokens("你好世界"));
        }

        @Test
        void mixedCjkEnglish() {
            assertEquals(3, TokenCounter.estimateTokens("Hello你好"));
        }

        @Test
        void longText() {
            assertEquals(250, TokenCounter.estimateTokens("a".repeat(1000)));
        }

        @Test
        void japaneseText() {
            int tokens = TokenCounter.estimateTokens("こんにちは");
            assertTrue(tokens > 0);
            assertTrue(tokens <= 5);
        }
    }

    @Nested
    class TestEstimateMessageTokens {
        @Test
        void stringContent() {
            Message msg = Message.user("Hello World");
            assertEquals(7, TokenCounter.estimateMessageTokens(msg));
        }

        @Test
        void contentBlocks() {
            Message msg = Message.assistant(List.of(new TextBlock("Hello World")));
            assertEquals(7, TokenCounter.estimateMessageTokens(msg));
        }

        @Test
        void toolUseBlocks() {
            Message msg = Message.assistant(List.of(
                    new ToolUseBlock("123", "read_file", Map.of("file_path", "test.txt"))));
            assertTrue(TokenCounter.estimateMessageTokens(msg) > 4);
        }

        @Test
        void toolResultBlocks() {
            Message msg = Message.userBlocks(List.of(
                    new ToolResultBlock("123", "file contents here")));
            assertTrue(TokenCounter.estimateMessageTokens(msg) > 4);
        }
    }

    @Nested
    class TestEstimateConversationTokens {
        @Test
        void messagesOnly() {
            var messages = List.of(
                    Message.user("Hello"),
                    new Message("assistant", "Hi there"));
            assertTrue(TokenCounter.estimateConversationTokens(messages, null, null) > 0);
        }

        @Test
        void withSystemPrompt() {
            var messages = List.of(Message.user("Hi"));
            int withSystem = TokenCounter.estimateConversationTokens(messages, "You are helpful.", null);
            int without = TokenCounter.estimateConversationTokens(messages, null, null);
            assertTrue(withSystem > without);
        }

        @Test
        void withTools() {
            var messages = List.of(Message.user("Hi"));
            var tools = List.of(new Tool("read_file", "Read a file", Map.of("type", "object")));
            int withTools = TokenCounter.estimateConversationTokens(messages, null, tools);
            int without = TokenCounter.estimateConversationTokens(messages, null, null);
            assertTrue(withTools > without);
        }

        @Test
        void emptyConversation() {
            assertEquals(0, TokenCounter.estimateConversationTokens(List.of(), null, null));
        }
    }

    @Nested
    class TestGetModelContextLimit {
        @Test
        void knownModels() {
            assertEquals(64_000, TokenCounter.getModelContextLimit("deepseek-chat"));
            assertEquals(128_000, TokenCounter.getModelContextLimit("gpt-4o"));
            assertEquals(200_000, TokenCounter.getModelContextLimit("claude-sonnet-4-20250514"));
        }

        @Test
        void unknownModel() {
            assertEquals(AppConfig.defaultContextWindow(), TokenCounter.getModelContextLimit("unknown-model"));
        }
    }

    @Nested
    class TestContextBudget {
        @Test
        void defaults() {
            assertEquals(64_000, TokenCounter.ContextBudget.DEFAULT.maxContextTokens());
            assertEquals(4096, TokenCounter.ContextBudget.DEFAULT.reservedForResponse());
        }

        @Test
        void remainingBudget() {
            var budget = new TokenCounter.ContextBudget(10000, 2000);
            assertEquals(5000, TokenCounter.remainingBudget(budget, 3000));
        }

        @Test
        void noNegativeRemaining() {
            var budget = new TokenCounter.ContextBudget(1000, 500);
            assertEquals(0, TokenCounter.remainingBudget(budget, 2000));
        }

        @Test
        void overBudget() {
            var budget = new TokenCounter.ContextBudget(10000, 2000);
            assertFalse(TokenCounter.isOverBudget(budget, 7000));
            assertTrue(TokenCounter.isOverBudget(budget, 8000));
            assertTrue(TokenCounter.isOverBudget(budget, 9000));
        }
    }
}

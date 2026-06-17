package com.aicode.agent;

import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.StreamEvent;
import com.aicode.agent.llm.TextBlock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ErrorsTest {
    static class StatusException extends Exception {
        private final int status;

        StatusException(String message, int status) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    static final ChatResponse SUCCESS_RESPONSE = new ChatResponse(
            List.of(new TextBlock("OK")),
            "OK",
            "end_turn",
            Map.of("input_tokens", 5, "output_tokens", 3)
    );

    @Nested
    class TestIsRetryable {
        @Test
        void retryOnNetworkErrors() {
            assertTrue(Errors.isRetryable(new Exception("network error")));
            assertTrue(Errors.isRetryable(new Exception("connection reset")));
            assertTrue(Errors.isRetryable(new Exception("request timeout")));
        }

        @Test
        void retryOnRateLimit() {
            assertTrue(Errors.isRetryable(new Exception("rate limit exceeded")));
            assertTrue(Errors.isRetryable(new Exception("429 Too Many Requests")));
        }

        @Test
        void retryOnServerErrors() {
            assertTrue(Errors.isRetryable(new Exception("500 Internal Server Error")));
            assertTrue(Errors.isRetryable(new Exception("502 Bad Gateway")));
            assertTrue(Errors.isRetryable(new Exception("503 Service Unavailable")));
        }

        @Test
        void retryOnStatus500() {
            assertTrue(Errors.isRetryable(new StatusException("server", 500)));
        }

        @Test
        void retryOnStatus429() {
            assertTrue(Errors.isRetryable(new StatusException("limited", 429)));
        }

        @Test
        void noRetryOnClientErrors() {
            assertFalse(Errors.isRetryable(new Exception("invalid api key")));
            assertFalse(Errors.isRetryable(new Exception("400 Bad Request")));
        }

        @Test
        void noRetryOnStatus400() {
            assertFalse(Errors.isRetryable(new StatusException("bad", 400)));
        }
    }

    @Nested
    class TestCalculateDelay {
        @Test
        void valueWithinRange() {
            Errors.RetryConfig config = new Errors.RetryConfig(3, 0.1, 5.0);
            for (int i = 0; i < 100; i++) {
                double delay = Errors.calculateDelay(0, config);
                assertTrue(delay >= 0 && delay <= 0.1);
            }
        }

        @Test
        void capAtMaxDelay() {
            Errors.RetryConfig config = new Errors.RetryConfig(3, 1.0, 2.0);
            for (int i = 0; i < 100; i++) {
                double delay = Errors.calculateDelay(10, config);
                assertTrue(delay <= 2.0);
            }
        }
    }

    @Nested
    class TestRetryProvider {
        @Test
        void returnOnFirstSuccess() {
            LLMProvider inner = new LLMProvider() {
                @Override
                public CompletableFuture<ChatResponse> chat(List<Message> messages,
                        com.aicode.agent.llm.ChatOptions options) {
                    return CompletableFuture.completedFuture(SUCCESS_RESPONSE);
                }

                @Override
                public void stream(List<Message> messages, com.aicode.agent.llm.ChatOptions options,
                        java.util.function.Consumer<com.aicode.agent.llm.StreamEvent> consumer) {
                    throw new UnsupportedOperationException();
                }
            };
            AtomicInteger calls = new AtomicInteger();
            LLMProvider tracking = new LLMProvider() {
                @Override
                public CompletableFuture<ChatResponse> chat(List<Message> messages,
                        com.aicode.agent.llm.ChatOptions options) {
                    calls.incrementAndGet();
                    return inner.chat(messages, options);
                }

                @Override
                public void stream(List<Message> messages, com.aicode.agent.llm.ChatOptions options,
                        java.util.function.Consumer<com.aicode.agent.llm.StreamEvent> consumer) {
                    throw new UnsupportedOperationException();
                }
            };

            var provider = new Errors.RetryProvider(
                    tracking, new Errors.RetryConfig(3, 0.001, 0.001));
            ChatResponse result = provider.chat(List.of(), new ChatOptions()).join();
            assertEquals("OK", result.text());
            assertEquals(1, calls.get());
        }

        @Test
        void retryAndSucceed() {
            AtomicInteger callCount = new AtomicInteger();
            LLMProvider inner = new LLMProvider() {
                @Override
                public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
                    int count = callCount.incrementAndGet();
                    if (count < 3) {
                        return CompletableFuture.failedFuture(new Exception("503 Service Unavailable"));
                    }
                    return CompletableFuture.completedFuture(SUCCESS_RESPONSE);
                }

                @Override
                public void stream(List<Message> messages, ChatOptions options,
                        java.util.function.Consumer<StreamEvent> consumer) {
                    throw new UnsupportedOperationException();
                }
            };

            var provider = new Errors.RetryProvider(
                    inner, new Errors.RetryConfig(3, 0.001, 0.001));
            ChatResponse result = provider.chat(List.of(), new ChatOptions()).join();
            assertEquals("OK", result.text());
            assertEquals(3, callCount.get());
        }

        @Test
        void throwAfterMaxRetries() {
            AtomicInteger callCount = new AtomicInteger();
            LLMProvider inner = new LLMProvider() {
                @Override
                public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
                    callCount.incrementAndGet();
                    return CompletableFuture.failedFuture(new Exception("500 Internal Server Error"));
                }

                @Override
                public void stream(List<Message> messages, ChatOptions options,
                        java.util.function.Consumer<StreamEvent> consumer) {
                    throw new UnsupportedOperationException();
                }
            };

            var provider = new Errors.RetryProvider(
                    inner, new Errors.RetryConfig(2, 0.001, 0.001));

            Exception ex = assertThrows(Exception.class,
                    () -> provider.chat(List.of(), new ChatOptions()).join());
            assertTrue(ex.getMessage().contains("500"));
            assertEquals(3, callCount.get());
        }

        @Test
        void noRetryOnNonRetryable() {
            AtomicInteger callCount = new AtomicInteger();
            LLMProvider inner = new LLMProvider() {
                @Override
                public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
                    callCount.incrementAndGet();
                    return CompletableFuture.failedFuture(new Exception("invalid api key"));
                }

                @Override
                public void stream(List<Message> messages, ChatOptions options,
                        java.util.function.Consumer<StreamEvent> consumer) {
                    throw new UnsupportedOperationException();
                }
            };

            var provider = new Errors.RetryProvider(
                    inner, new Errors.RetryConfig(3, 0.001, 0.001));

            Exception ex = assertThrows(Exception.class,
                    () -> provider.chat(List.of(), new ChatOptions()).join());
            assertTrue(ex.getMessage().contains("invalid api key"));
            assertEquals(1, callCount.get());
        }
    }

    @Nested
    class TestSafeToolExecutor {
        @Test
        void returnResultOnSuccess() {
            Agent.ToolExecutor executor = (name, input) ->
                    CompletableFuture.completedFuture("result from " + name);
            Agent.ToolExecutor safe = Errors.safeToolExecutor(executor, null);
            String result = safe.execute("read_file", Map.of("file_path", "test.txt")).join();
            assertEquals("result from read_file", result);
        }

        @Test
        void catchErrors() {
            Agent.ToolExecutor executor = (name, input) ->
                    CompletableFuture.failedFuture(new RuntimeException("file not found"));
            Agent.ToolExecutor safe = Errors.safeToolExecutor(executor, null);
            String result = safe.execute("read_file", Map.of("file_path", "missing.txt")).join();
            assertTrue(result.contains("Error executing read_file"));
            assertTrue(result.contains("file not found"));
        }

        @Test
        void rejectUnknownTools() {
            Agent.ToolExecutor executor = (name, input) -> CompletableFuture.completedFuture("result");
            Agent.ToolExecutor safe = Errors.safeToolExecutor(
                    executor, Set.of("read_file", "write_file"));
            String result = safe.execute("delete_file", Map.of()).join();
            assertTrue(result.contains("unknown tool \"delete_file\""));
            assertTrue(result.contains("read_file"));
        }

        @Test
        void allowKnownTools() {
            Agent.ToolExecutor executor = (name, input) -> CompletableFuture.completedFuture("ok");
            Agent.ToolExecutor safe = Errors.safeToolExecutor(executor, Set.of("read_file"));
            String result = safe.execute("read_file", Map.of()).join();
            assertEquals("ok", result);
        }

        @Test
        void noCheckWhenKnownToolsNotSet() {
            Agent.ToolExecutor executor = (name, input) -> CompletableFuture.completedFuture("ok");
            Agent.ToolExecutor safe = Errors.safeToolExecutor(executor, null);
            String result = safe.execute("any_tool", Map.of()).join();
            assertEquals("ok", result);
        }
    }

    @Nested
    class TestIsToolErrorResult {
        @Test
        void detectsSafeExecutorErrors() {
            assertTrue(Errors.isToolErrorResult("Error: unknown tool \"x\""));
            assertTrue(Errors.isToolErrorResult("Error executing read: file not found"));
            assertFalse(Errors.isToolErrorResult("ok"));
            assertFalse(Errors.isToolErrorResult(""));
            assertFalse(Errors.isToolErrorResult(null));
        }
    }
}

package com.aicode.agent;

import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.LLMHttpException;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.StreamEvent;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

/**
 * Retry wrapper and safe tool executor.
 * Mirrors Python {@code errors.py} and TypeScript {@code errors.ts}.
 */
public final class Errors {
    public record RetryConfig(int maxRetries, double baseDelaySeconds, double maxDelaySeconds) {
        public static final RetryConfig DEFAULT = new RetryConfig(3, 1.0, 10.0);
    }

    private Errors() {}

    public static double calculateDelay(int attempt, RetryConfig config) {
        double exponential = config.baseDelaySeconds() * Math.pow(2, attempt);
        double capped = Math.min(exponential, config.maxDelaySeconds());
        return RandomGenerator.getDefault().nextDouble() * capped;
    }

    /**
     * Check if an error is retryable (network/server errors).
     * Aligns with Python {@code is_retryable} and TypeScript {@code isRetryable}.
     */
    public static boolean isRetryable(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof LLMHttpException http) {
            int status = http.statusCode();
            return status >= 500 || status == 429;
        }

        String msg = cause.getMessage() != null ? cause.getMessage().toLowerCase() : "";
        if (msg.contains("network") || msg.contains("connection") || msg.contains("econnreset")
                || msg.contains("timeout")) {
            return true;
        }
        if (msg.contains("rate limit") || msg.contains("429")) {
            return true;
        }
        if (msg.contains("500") || msg.contains("502") || msg.contains("503")) {
            return true;
        }

        Integer status = extractStatus(cause);
        if (status != null) {
            return status >= 500 || status == 429;
        }
        return false;
    }

    private static Integer extractStatus(Throwable error) {
        if (error instanceof LLMHttpException http) {
            return http.statusCode();
        }
        for (String methodName : List.of("getStatus", "getStatusCode", "status", "statusCode")) {
            try {
                Method m = findMethod(error.getClass(), methodName);
                if (m != null) {
                    Object value = m.invoke(error);
                    if (value instanceof Number n) {
                        return n.intValue();
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // try next
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name) {
        for (Method m : type.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) {
                return m;
            }
        }
        return null;
    }

    public static class RetryProvider implements LLMProvider {
        private final LLMProvider provider;
        private final RetryConfig config;

        public RetryProvider(LLMProvider provider) {
            this(provider, RetryConfig.DEFAULT);
        }

        public RetryProvider(LLMProvider provider, RetryConfig config) {
            this.provider = provider;
            this.config = config;
        }

        @Override
        public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
            return chatWithRetry(messages, options, 0);
        }

        private CompletableFuture<ChatResponse> chatWithRetry(
                List<Message> messages, ChatOptions options, int attempt
        ) {
            return provider.chat(messages, options).exceptionallyCompose(error -> {
                Throwable cause = unwrap(error);
                if (!isRetryable(cause) || attempt >= config.maxRetries()) {
                    return CompletableFuture.failedFuture(cause);
                }
                double delay = calculateDelay(attempt, config);
                CompletableFuture<Void> wait = new CompletableFuture<>();
                CompletableFuture.delayedExecutor((long) (delay * 1000), TimeUnit.MILLISECONDS)
                        .execute(() -> wait.complete(null));
                return wait.thenCompose(v -> chatWithRetry(messages, options, attempt + 1));
            });
        }

        @Override
        public void stream(List<Message> messages, ChatOptions options, Consumer<StreamEvent> consumer) {
            streamWithRetry(messages, options, consumer, 0);
        }

        private void streamWithRetry(
                List<Message> messages, ChatOptions options,
                Consumer<StreamEvent> consumer, int attempt
        ) {
            try {
                provider.stream(messages, options, consumer);
            } catch (Exception e) {
                if (!isRetryable(e) || attempt >= config.maxRetries()) {
                    throw e instanceof RuntimeException re ? re : new RuntimeException(e);
                }
                double delay = calculateDelay(attempt, config);
                try {
                    Thread.sleep((long) (delay * 1000));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
                streamWithRetry(messages, options, consumer, attempt + 1);
            }
        }
    }

    private static Throwable unwrap(Throwable error) {
        if (error instanceof java.util.concurrent.CompletionException ce && ce.getCause() != null) {
            return ce.getCause();
        }
        return error;
    }

    /** Whether a tool executor result string represents a failure (from {@link #safeToolExecutor}). */
    public static boolean isToolErrorResult(String result) {
        if (result == null || result.isBlank()) {
            return false;
        }
        return result.startsWith("Error:") || result.startsWith("Error executing ");
    }

    public static Agent.ToolExecutor safeToolExecutor(Agent.ToolExecutor executor, Set<String> knownTools) {
        return (name, input) -> {
            if (knownTools != null && !knownTools.contains(name)) {
                String available = String.join(", ", knownTools.stream().sorted().toList());
                return CompletableFuture.completedFuture(
                        "Error: unknown tool \"" + name + "\". Available tools: " + available
                );
            }
            return executor.execute(name, input).handle((result, error) -> {
                if (error != null) {
                    Throwable cause = unwrap(error);
                    String message = cause.getMessage() != null ? cause.getMessage() : cause.toString();
                    return "Error executing " + name + ": " + message;
                }
                return result;
            });
        };
    }
}

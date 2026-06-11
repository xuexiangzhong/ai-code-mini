package com.aicode.agent;

import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.StreamEvent;
import com.aicode.agent.llm.Tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

final class TestFixtures {
    static final Tool TEST_TOOL = new Tool(
            "test_tool",
            "A test tool",
            Map.of(
                    "type", "object",
                    "properties", Map.of("query", Map.of("type", "string")),
                    "required", List.of("query")
            )
    );

    private TestFixtures() {}

    static LLMProvider mockProvider(List<ChatResponse> responses) {
        AtomicInteger index = new AtomicInteger();
        return new LLMProvider() {
            @Override
            public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
                int i = index.getAndIncrement();
                if (i >= responses.size()) {
                    return CompletableFuture.failedFuture(new IllegalStateException("No more responses"));
                }
                return CompletableFuture.completedFuture(responses.get(i));
            }

            @Override
            public void stream(List<Message> messages, ChatOptions options, Consumer<StreamEvent> consumer) {
                throw new UnsupportedOperationException("stream not mocked");
            }
        };
    }

    static LLMProvider trackingProvider(List<ChatResponse> responses, CopyOnWriteArrayList<List<Message>> calls) {
        AtomicInteger index = new AtomicInteger();
        return new LLMProvider() {
            @Override
            public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
                calls.add(List.copyOf(messages));
                int i = index.getAndIncrement();
                return CompletableFuture.completedFuture(responses.get(i));
            }

            @Override
            public void stream(List<Message> messages, ChatOptions options, Consumer<StreamEvent> consumer) {
                throw new UnsupportedOperationException("stream not mocked");
            }
        };
    }

    static Agent.AgentConfig baseConfig(LLMProvider provider, Agent.ToolExecutor executor) {
        return new Agent.AgentConfig(
                provider,
                "You are a test assistant.",
                List.of(TEST_TOOL),
                executor != null ? executor : (name, input) -> CompletableFuture.completedFuture("tool result")
        );
    }
}

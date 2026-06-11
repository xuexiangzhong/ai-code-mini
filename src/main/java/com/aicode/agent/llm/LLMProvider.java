package com.aicode.agent.llm;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Unified interface for LLM providers.
 */
public interface LLMProvider {
    CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options);

    void stream(List<Message> messages, ChatOptions options, Consumer<StreamEvent> consumer);
}

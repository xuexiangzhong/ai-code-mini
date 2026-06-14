package com.aicode.agent.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAICompatibleEmbeddingProviderTest {
    @Test
    void resolvesEmbeddingsUrlFromChatCompletions() {
        assertEquals(
                "https://api.openai.com/v1/embeddings",
                OpenAICompatibleEmbeddingProvider.resolveEmbeddingsUrl(
                        "https://api.openai.com/v1/chat/completions")
        );
    }

    @Test
    void resolvesOllamaEmbeddingsUrl() {
        assertTrue(OpenAICompatibleEmbeddingProvider.resolveEmbeddingsUrl(
                "http://localhost:11434/v1").endsWith("/embeddings"));
    }
}

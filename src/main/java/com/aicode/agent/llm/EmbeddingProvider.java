package com.aicode.agent.llm;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EmbeddingProvider {
    CompletableFuture<float[]> embed(String text);

    default CompletableFuture<List<float[]>> embedBatch(List<String> texts) {
        return CompletableFuture.supplyAsync(() ->
                texts.stream()
                        .map(t -> embed(t).join())
                        .toList()
        );
    }
}

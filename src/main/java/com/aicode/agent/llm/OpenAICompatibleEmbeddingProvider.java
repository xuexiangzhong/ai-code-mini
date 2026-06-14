package com.aicode.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** OpenAI-compatible {@code /v1/embeddings} client (Ollama, OpenAI, etc.). */
public final class OpenAICompatibleEmbeddingProvider implements EmbeddingProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OkHttpClient client;
    private final String apiKey;
    private final String embeddingsUrl;
    private final String model;

    public record Config(String apiKey, String baseUrl, String model) {}

    public OpenAICompatibleEmbeddingProvider(Config config) {
        this(config, new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build());
    }

    public OpenAICompatibleEmbeddingProvider(Config config, OkHttpClient client) {
        this.apiKey = config.apiKey();
        this.embeddingsUrl = resolveEmbeddingsUrl(config.baseUrl());
        this.model = config.model() != null && !config.model().isBlank()
                ? config.model()
                : "text-embedding-3-small";
        this.client = client;
    }

    static String resolveEmbeddingsUrl(String baseUrl) {
        String normalized = baseUrl.replaceAll("/+$", "");
        if (normalized.endsWith("/chat/completions")) {
            return normalized.replace("/chat/completions", "/embeddings");
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/embeddings";
        }
        if (normalized.contains("/compatible-mode")) {
            return normalized + "/v1/embeddings";
        }
        return normalized + "/embeddings";
    }

    @Override
    public CompletableFuture<float[]> embed(String text) {
        return embedBatch(List.of(text)).thenApply(list -> list.getFirst());
    }

    @Override
    public CompletableFuture<List<float[]>> embedBatch(List<String> texts) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ObjectNode body = MAPPER.createObjectNode();
                body.put("model", model);
                ArrayNode input = body.putArray("input");
                for (String text : texts) {
                    input.add(text);
                }

                Request request = new Request.Builder()
                        .url(embeddingsUrl)
                        .addHeader("Authorization", "Bearer " + apiKey)
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        throw new IOException("Embedding API failed: HTTP " + response.code());
                    }
                    JsonNode root = MAPPER.readTree(response.body().string());
                    JsonNode data = root.get("data");
                    if (data == null || !data.isArray()) {
                        throw new IOException("Invalid embedding response");
                    }
                    List<float[]> vectors = new ArrayList<>();
                    for (JsonNode item : data) {
                        JsonNode embedding = item.get("embedding");
                        if (embedding == null || !embedding.isArray()) {
                            throw new IOException("Missing embedding vector");
                        }
                        float[] vec = new float[embedding.size()];
                        for (int i = 0; i < embedding.size(); i++) {
                            vec[i] = (float) embedding.get(i).asDouble();
                        }
                        vectors.add(vec);
                    }
                    return vectors;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}

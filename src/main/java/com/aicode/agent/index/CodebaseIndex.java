package com.aicode.agent.index;

import com.aicode.agent.llm.EmbeddingProvider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Hybrid codebase index: TF-IDF (always) + optional embedding vectors (when API available).
 */
public final class CodebaseIndex {
    public record SearchHit(CodebaseChunkCollector.Chunk chunk, double score, String method) {}

    public record SearchResult(List<SearchHit> hits, int filesScanned, boolean usedEmbeddings) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersistedChunk(String filePath, int startLine, int endLine, String text, float[] embedding) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersistedIndex(
            int version,
            String workspace,
            String embeddingModel,
            List<PersistedChunk> chunks
    ) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int VERSION = 1;
    private static final int EMBED_BATCH = 16;

    private final Path workspace;
    private final Path indexFile;
    private final TfIdfIndex tfIdfIndex;
    private final List<float[]> embeddings;
    private final boolean embeddingsReady;
    private final AtomicBoolean building = new AtomicBoolean(false);

    private CodebaseIndex(
            Path workspace,
            Path indexFile,
            TfIdfIndex tfIdfIndex,
            List<float[]> embeddings,
            boolean embeddingsReady
    ) {
        this.workspace = workspace;
        this.indexFile = indexFile;
        this.tfIdfIndex = tfIdfIndex;
        this.embeddings = embeddings;
        this.embeddingsReady = embeddingsReady;
    }

    public static CodebaseIndex loadOrBuild(Path workspace, Path indexDir) {
        Path indexFile = indexDir.resolve("index.json");
        if (Files.isRegularFile(indexFile)) {
            try {
                PersistedIndex persisted = MAPPER.readValue(indexFile.toFile(), PersistedIndex.class);
                if (workspace.toString().equals(persisted.workspace())) {
                    List<CodebaseChunkCollector.Chunk> chunks = persisted.chunks().stream()
                            .map(c -> new CodebaseChunkCollector.Chunk(
                                    c.filePath(), c.startLine(), c.endLine(), c.text()))
                            .toList();
                    List<float[]> embeddings = persisted.chunks().stream()
                            .map(PersistedChunk::embedding)
                            .toList();
                    boolean ready = embeddings.stream().anyMatch(e -> e != null && e.length > 0);
                    return new CodebaseIndex(workspace, indexFile, new TfIdfIndex(chunks), embeddings, ready);
                }
            } catch (IOException ignored) {
                // rebuild below
            }
        }

        List<CodebaseChunkCollector.Chunk> chunks = CodebaseChunkCollector.collect(workspace, null);
        List<float[]> emptyEmb = chunks.stream().map(c -> new float[0]).toList();
        return new CodebaseIndex(workspace, indexFile, new TfIdfIndex(chunks), emptyEmb, false);
    }

    public void ensureEmbeddingsAsync(EmbeddingProvider provider, String model) {
        if (embeddingsReady || !building.compareAndSet(false, true)) {
            return;
        }
        Thread.ofVirtual().name("codebase-embed-index").start(() -> {
            try {
                buildEmbeddings(provider, model);
            } finally {
                building.set(false);
            }
        });
    }

    private void buildEmbeddings(EmbeddingProvider provider, String model) {
        List<CodebaseChunkCollector.Chunk> chunks = tfIdfIndex.allChunks();
        if (chunks.isEmpty()) {
            return;
        }
        List<float[]> built = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += EMBED_BATCH) {
            int end = Math.min(chunks.size(), i + EMBED_BATCH);
            List<String> batch = chunks.subList(i, end).stream().map(CodebaseChunkCollector.Chunk::text).toList();
            try {
                List<float[]> vectors = provider.embedBatch(batch).join();
                built.addAll(vectors);
            } catch (Exception e) {
                return;
            }
        }
        if (built.size() != chunks.size()) {
            return;
        }
        persist(chunks, built, model);
    }

    private void persist(List<CodebaseChunkCollector.Chunk> chunks, List<float[]> vectors, String model) {
        try {
            Files.createDirectories(indexFile.getParent());
            List<PersistedChunk> persisted = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                CodebaseChunkCollector.Chunk c = chunks.get(i);
                persisted.add(new PersistedChunk(c.filePath(), c.startLine(), c.endLine(), c.text(), vectors.get(i)));
            }
            PersistedIndex index = new PersistedIndex(VERSION, workspace.toString(), model, persisted);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(indexFile.toFile(), index);
            CodebaseIndexManager.invalidate(workspace);
        } catch (IOException ignored) {
            // best-effort cache
        }
    }

    public SearchResult searchWithEmbedding(String query, float[] queryEmbedding, int limit) {
        List<CodebaseChunkCollector.Chunk> allChunks = tfIdfIndex.allChunks();
        Map<Integer, Double> combined = new HashMap<>();
        for (TfIdfIndex.ScoredChunk hit : tfIdfIndex.search(query, Math.max(limit * 3, 20))) {
            int idx = allChunks.indexOf(hit.chunk());
            if (idx >= 0) {
                combined.put(idx, hit.score() * (embeddingsReady ? 0.35 : 1.0));
            }
        }
        if (queryEmbedding != null && queryEmbedding.length > 0 && embeddingsReady) {
            for (int i = 0; i < embeddings.size(); i++) {
                float[] vec = embeddings.get(i);
                if (vec != null && vec.length > 0) {
                    double score = VectorMath.cosineSimilarity(queryEmbedding, vec);
                    if (score > 0) {
                        combined.merge(i, score * 0.65, Double::sum);
                    }
                }
            }
        }
        List<SearchHit> hits = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : combined.entrySet()) {
            int idx = entry.getKey();
            if (idx >= 0 && idx < allChunks.size()) {
                String method = embeddingsReady && queryEmbedding != null ? "hybrid" : "tfidf";
                hits.add(new SearchHit(allChunks.get(idx), entry.getValue(), method));
            }
        }
        hits.sort(Comparator.comparingDouble(SearchHit::score).reversed());
        int cap = Math.max(1, Math.min(limit, 25));
        return new SearchResult(hits.size() <= cap ? hits : hits.subList(0, cap), estimateFileCount(), embeddingsReady);
    }

    public CompletableFuture<SearchResult> searchAsync(
            String query,
            int limit,
            EmbeddingProvider provider
    ) {
        if (provider == null) {
            return CompletableFuture.completedFuture(searchWithEmbedding(query, null, limit));
        }
        return provider.embed(query).handle((queryVec, error) -> {
            if (error != null || queryVec == null) {
                return searchWithEmbedding(query, null, limit);
            }
            return searchWithEmbedding(query, queryVec, limit);
        });
    }

    private int estimateFileCount() {
        return (int) tfIdfIndex.allChunks().stream()
                .map(CodebaseChunkCollector.Chunk::filePath)
                .distinct()
                .count();
    }

    public boolean embeddingsReady() {
        return embeddingsReady;
    }

    public int chunkCount() {
        return tfIdfIndex.size();
    }
}

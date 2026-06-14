package com.aicode.agent.index;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** TF-IDF vectors for codebase chunks. */
public final class TfIdfIndex {
    public record ScoredChunk(CodebaseChunkCollector.Chunk chunk, double score) {}

    private final List<CodebaseChunkCollector.Chunk> chunks;
    private final List<Map<String, Double>> vectors;
    private final Map<String, Double> idf;

    public TfIdfIndex(List<CodebaseChunkCollector.Chunk> chunks) {
        this.chunks = List.copyOf(chunks);
        this.idf = computeIdf(chunks);
        this.vectors = new ArrayList<>();
        for (CodebaseChunkCollector.Chunk chunk : chunks) {
            vectors.add(toTfIdfVector(tokenizeChunk(chunk), idf));
        }
    }

    public List<ScoredChunk> search(String query, int limit) {
        List<String> terms = CodebaseChunkCollector.tokenize(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        Map<String, Double> queryVec = toTfIdfVector(terms, idf);
        List<ScoredChunk> hits = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            double score = VectorMath.cosineSimilarity(queryVec, vectors.get(i));
            if (score > 0) {
                hits.add(new ScoredChunk(chunks.get(i), score));
            }
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        return hits.size() <= limit ? hits : hits.subList(0, limit);
    }

    public int size() {
        return chunks.size();
    }

    public List<CodebaseChunkCollector.Chunk> allChunks() {
        return chunks;
    }

    private static Map<String, Double> computeIdf(List<CodebaseChunkCollector.Chunk> chunks) {
        Map<String, Integer> docFreq = new HashMap<>();
        for (CodebaseChunkCollector.Chunk chunk : chunks) {
            for (String term : uniqueTerms(tokenizeChunk(chunk))) {
                docFreq.merge(term, 1, Integer::sum);
            }
        }
        int n = Math.max(1, chunks.size());
        Map<String, Double> idf = new HashMap<>();
        for (Map.Entry<String, Integer> entry : docFreq.entrySet()) {
            idf.put(entry.getKey(), Math.log((n + 1.0) / (entry.getValue() + 1.0)) + 1.0);
        }
        return idf;
    }

    private static Map<String, Double> toTfIdfVector(List<String> terms, Map<String, Double> idf) {
        Map<String, Integer> tf = new HashMap<>();
        for (String term : terms) {
            tf.merge(term, 1, Integer::sum);
        }
        Map<String, Double> vec = new HashMap<>();
        for (Map.Entry<String, Integer> entry : tf.entrySet()) {
            double weight = entry.getValue() * idf.getOrDefault(entry.getKey(), 1.0);
            vec.put(entry.getKey(), weight);
        }
        return vec;
    }

    private static List<String> tokenizeChunk(CodebaseChunkCollector.Chunk chunk) {
        List<String> terms = new ArrayList<>(CodebaseChunkCollector.tokenize(chunk.text()));
        for (String part : CodebaseChunkCollector.tokenize(chunk.filePath())) {
            terms.add(part);
        }
        return terms;
    }

    private static List<String> uniqueTerms(List<String> terms) {
        return terms.stream().distinct().toList();
    }
}

package com.aicode.agent.tools;

import com.aicode.agent.index.CodebaseIndex;
import com.aicode.agent.index.CodebaseIndexManager;
import com.aicode.agent.index.CodebaseChunkCollector;
import com.aicode.agent.llm.EmbeddingProvider;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Codebase search facade: hybrid TF-IDF + embedding index when available.
 */
public final class CodebaseSearch {
    public record Chunk(String filePath, int startLine, int endLine, String text, double score) {}

    public record Result(List<Chunk> chunks, int filesScanned, String method) {}

    private CodebaseSearch() {}

    public static Result search(Path root, String query, int limit) {
        return search(root, query, limit, null, null);
    }

    public static Result search(Path root, String query, int limit, String pathFilter) {
        return search(root, query, limit, pathFilter, null);
    }

    public static Result search(
            Path root,
            String query,
            int limit,
            String pathFilter,
            EmbeddingProvider embeddingProvider
    ) {
        if (pathFilter != null && !pathFilter.isBlank()) {
            return legacyKeywordSearch(root, query, limit, pathFilter);
        }

        CodebaseIndex.SearchResult indexed = CodebaseIndexManager.search(root, query, limit, embeddingProvider);
        if (indexed.hits().isEmpty() && indexed.filesScanned() == 0) {
            return legacyKeywordSearch(root, query, limit, null);
        }

        List<Chunk> chunks = indexed.hits().stream()
                .map(hit -> new Chunk(
                        hit.chunk().filePath(),
                        hit.chunk().startLine(),
                        hit.chunk().endLine(),
                        hit.chunk().text(),
                        hit.score()))
                .toList();
        String method = indexed.usedEmbeddings() ? "hybrid (TF-IDF + embedding)" : "TF-IDF";
        return new Result(chunks, indexed.filesScanned(), method);
    }

    public static String formatResults(Result result) {
        if (result.chunks().isEmpty()) {
            return "No matching code found. Try different keywords or use grep for exact symbols.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(result.chunks().size())
                .append(" relevant chunk(s) via ").append(result.method())
                .append(" (scanned ~").append(result.filesScanned()).append(" files):\n\n");
        int i = 1;
        for (Chunk chunk : result.chunks()) {
            sb.append(i++).append(". ")
                    .append(chunk.filePath())
                    .append(" (lines ").append(chunk.startLine()).append("-").append(chunk.endLine())
                    .append(", score ").append(String.format(Locale.ROOT, "%.2f", chunk.score()))
                    .append(")\n```\n")
                    .append(chunk.text().strip())
                    .append("\n```\n\n");
        }
        return sb.toString().stripTrailing();
    }

    private static Result legacyKeywordSearch(Path root, String query, int limit, String pathFilter) {
        List<CodebaseChunkCollector.Chunk> raw = CodebaseChunkCollector.collect(root, pathFilter);
        List<String> terms = CodebaseChunkCollector.tokenize(query);
        if (terms.isEmpty()) {
            return new Result(List.of(), 0, "keyword");
        }
        List<Chunk> hits = new java.util.ArrayList<>();
        for (CodebaseChunkCollector.Chunk chunk : raw) {
            double score = scoreChunk(chunk.filePath(), chunk.text(), terms);
            if (score > 0) {
                hits.add(new Chunk(chunk.filePath(), chunk.startLine(), chunk.endLine(), chunk.text(), score));
            }
        }
        hits.sort((a, b) -> Double.compare(b.score(), a.score()));
        int cap = Math.max(1, Math.min(limit, 25));
        List<Chunk> top = hits.size() <= cap ? hits : hits.subList(0, cap);
        int files = (int) raw.stream().map(CodebaseChunkCollector.Chunk::filePath).distinct().count();
        return new Result(top, files, "keyword");
    }

    private static double scoreChunk(String relativePath, String text, List<String> terms) {
        String lowerPath = relativePath.toLowerCase(Locale.ROOT);
        String lowerText = text.toLowerCase(Locale.ROOT);
        double score = 0;
        for (String term : terms) {
            if (lowerPath.contains(term)) {
                score += 2.5;
            }
            int count = countOccurrences(lowerText, term);
            if (count > 0) {
                score += count * (1.0 + Math.min(3, term.length() / 4.0));
            }
        }
        return score;
    }

    private static int countOccurrences(String text, String term) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(term, idx)) >= 0) {
            count++;
            idx += Math.max(1, term.length());
        }
        return count;
    }
}

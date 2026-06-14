package com.aicode.agent.index;

import com.aicode.agent.llm.EmbeddingProvider;
import com.aicode.agent.llm.OpenAICompatibleEmbeddingProvider;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public final class CodebaseIndexManager {
    private static final ConcurrentHashMap<String, CodebaseIndex> CACHE = new ConcurrentHashMap<>();

    private CodebaseIndexManager() {}

    public static CodebaseIndex get(Path workspace) {
        String key = workspaceKey(workspace.toString());
        Path indexDir = Path.of(System.getProperty("user.home"), ".aicode", "index", key);
        return CACHE.computeIfAbsent(key, k -> CodebaseIndex.loadOrBuild(workspace, indexDir));
    }

    public static void warmIndex(Path workspace, String apiKey, String baseUrl, String embeddingModel) {
        CodebaseIndex index = get(workspace);
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        String model = embeddingModel != null && !embeddingModel.isBlank()
                ? embeddingModel
                : System.getenv().getOrDefault("EMBEDDING_MODEL", "text-embedding-3-small");
        EmbeddingProvider provider = new OpenAICompatibleEmbeddingProvider(
                new OpenAICompatibleEmbeddingProvider.Config(apiKey, baseUrl, model)
        );
        index.ensureEmbeddingsAsync(provider, model);
    }

    public static CodebaseIndex.SearchResult search(
            Path workspace,
            String query,
            int limit,
            EmbeddingProvider provider
    ) {
        CodebaseIndex index = get(workspace);
        if (provider != null) {
            return index.searchAsync(query, limit, provider).join();
        }
        return index.searchWithEmbedding(query, null, limit);
    }

    public static void invalidate(Path workspace) {
        CACHE.remove(workspaceKey(workspace.toString()));
    }

    private static String workspaceKey(String workspace) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(workspace.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(workspace.hashCode());
        }
    }
}

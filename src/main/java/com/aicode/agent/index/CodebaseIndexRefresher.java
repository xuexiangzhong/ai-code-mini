package com.aicode.agent.index;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Debounced codebase index rebuild after file changes. */
public final class CodebaseIndexRefresher {
    private static final long DEBOUNCE_MS = 3_000;
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "codebase-index-refresher");
                t.setDaemon(true);
                return t;
            });

    private static final ConcurrentHashMap<String, ScheduledFuture<?>> PENDING = new ConcurrentHashMap<>();
    private static volatile Supplier<EmbeddingConfig> embeddingConfigSupplier = () -> null;

    public record EmbeddingConfig(String apiKey, String baseUrl, String embeddingModel) {}

    private CodebaseIndexRefresher() {}

    public static void configure(Supplier<EmbeddingConfig> supplier) {
        embeddingConfigSupplier = supplier != null ? supplier : () -> null;
    }

    public static void scheduleRefresh(Path workspace) {
        if (workspace == null || !Files.isDirectory(workspace)) {
            return;
        }
        String key = workspace.toAbsolutePath().normalize().toString();
        ScheduledFuture<?> existing = PENDING.remove(key);
        if (existing != null) {
            existing.cancel(false);
        }
        ScheduledFuture<?> scheduled = SCHEDULER.schedule(
                () -> {
                    PENDING.remove(key);
                    forceRebuild(workspace);
                },
                DEBOUNCE_MS,
                TimeUnit.MILLISECONDS
        );
        PENDING.put(key, scheduled);
    }

    public static void forceRebuild(Path workspace) {
        Path normalized = workspace.toAbsolutePath().normalize();
        CodebaseIndexManager.invalidate(normalized);
        try {
            Path indexDir = CodebaseIndexManager.indexDirFor(normalized);
            Files.deleteIfExists(indexDir.resolve("index.json"));
        } catch (Exception ignored) {
            // best-effort
        }
        CodebaseIndexManager.get(normalized);
        EmbeddingConfig config = embeddingConfigSupplier.get();
        if (config != null && config.apiKey() != null && !config.apiKey().isBlank()) {
            CodebaseIndexManager.warmIndex(
                    normalized,
                    config.apiKey(),
                    config.baseUrl(),
                    config.embeddingModel()
            );
        }
    }

    static void cancelAllForTests() {
        PENDING.values().forEach(f -> f.cancel(false));
        PENDING.clear();
    }
}

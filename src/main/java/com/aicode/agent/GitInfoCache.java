package com.aicode.agent;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** TTL cache for git metadata to avoid subprocess spam on every message. */
public final class GitInfoCache {
    private static final long TTL_MS = 30_000;
    private static final ConcurrentHashMap<Path, Entry> CACHE = new ConcurrentHashMap<>();

    private record Entry(Safety.GitInfo info, long expiresAtMillis) {}

    private GitInfoCache() {}

    public static Safety.GitInfo get(Path workspace, Supplier<Safety.GitInfo> loader) {
        Path key = workspace.toAbsolutePath().normalize();
        long now = System.currentTimeMillis();
        Entry cached = CACHE.get(key);
        if (cached != null && cached.expiresAtMillis() > now) {
            return cached.info();
        }
        Safety.GitInfo fresh = loader.get();
        CACHE.put(key, new Entry(fresh, now + TTL_MS));
        return fresh;
    }

    public static void invalidate(Path workspace) {
        CACHE.remove(workspace.toAbsolutePath().normalize());
    }

    static void clearForTests() {
        CACHE.clear();
    }
}

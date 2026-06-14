package com.aicode.agent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GitInfoCacheTest {
    @TempDir
    Path workspace;

    @AfterEach
    void tearDown() {
        GitInfoCache.clearForTests();
    }

    @Test
    void cachesWithinTtl() {
        AtomicInteger loads = new AtomicInteger();
        Safety.GitInfo info = Safety.parseGitInfo("main", "abc", "", "");
        Safety.GitInfo first = GitInfoCache.get(workspace, () -> {
            loads.incrementAndGet();
            return info;
        });
        Safety.GitInfo second = GitInfoCache.get(workspace, () -> {
            loads.incrementAndGet();
            return info;
        });
        assertEquals("main", first.branch());
        assertEquals(first, second);
        assertEquals(1, loads.get());
    }

    @Test
    void invalidateForcesReload() {
        AtomicInteger loads = new AtomicInteger();
        GitInfoCache.get(workspace, () -> {
            loads.incrementAndGet();
            return Safety.parseGitInfo("main", "", "", "");
        });
        GitInfoCache.invalidate(workspace);
        GitInfoCache.get(workspace, () -> {
            loads.incrementAndGet();
            return Safety.parseGitInfo("dev", "", "", "");
        });
        assertEquals(2, loads.get());
    }
}

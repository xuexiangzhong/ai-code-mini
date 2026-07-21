package com.aicode.agent.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CodebaseIndexRefresherTest {

    @Test
    void forceRebuildRefreshesTfidfIndex(@TempDir Path workspace) throws Exception {
        Path src = workspace.resolve("HelloWorld.java");
        Files.writeString(src, "public class HelloWorld { void uniqueMarkerAlpha() {} }");

        CodebaseIndex.SearchResult before = CodebaseIndexManager.search(
                workspace, "uniqueMarkerAlpha", 5, null);
        assertFalse(before.hits().isEmpty());

        Files.writeString(src, "public class HelloWorld { void uniqueMarkerBeta() {} }");
        CodebaseIndexRefresher.forceRebuild(workspace);

        CodebaseIndex.SearchResult stale = CodebaseIndexManager.search(
                workspace, "uniqueMarkerAlpha", 5, null);
        CodebaseIndex.SearchResult fresh = CodebaseIndexManager.search(
                workspace, "uniqueMarkerBeta", 5, null);

        assertTrue(stale.hits().isEmpty() || stale.hits().getFirst().score() < fresh.hits().getFirst().score());
        assertFalse(fresh.hits().isEmpty());
    }
}

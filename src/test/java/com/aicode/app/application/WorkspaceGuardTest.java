package com.aicode.app.application;

import com.aicode.agent.Safety;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceGuardTest {
    @TempDir
    Path workspace;

    private WorkspaceGuard guard;

    @BeforeEach
    void setUp() {
        Safety.FileSystemSandbox sandbox = new Safety.FileSystemSandbox(
                List.of(workspace.toString(), System.getProperty("java.io.tmpdir"))
        );
        guard = new WorkspaceGuard(workspace, sandbox);
    }

    @Test
    void resolvesRelativePathsAgainstWorkspace() {
        Path resolved = guard.resolve("src/main.java");
        assertEquals(workspace.resolve("src/main.java").toAbsolutePath().normalize(), resolved);
    }

    @Test
    void blocksPathsOutsideWorkspace() {
        assertNotNull(guard.validate("/etc/passwd"));
    }

    @Test
    void allowsPathsInsideWorkspace() {
        assertNull(guard.validate("pom.xml"));
    }
}

class DefaultToolExecutorSandboxTest {
    @TempDir
    Path workspace;

    @Test
    void globBlocksOutsideWorkspace() {
        DefaultToolExecutor executor = newExecutor();
        CompletableFuture<String> result = executor.execute(
                "glob",
                Map.of("pattern", "*.txt", "path", "/etc")
        );
        assertTrue(result.join().contains("Blocked"));
    }

    @Test
    void grepBlocksOutsideWorkspace() {
        DefaultToolExecutor executor = newExecutor();
        CompletableFuture<String> result = executor.execute(
                "grep",
                Map.of("pattern", "root", "path", "/etc")
        );
        assertTrue(result.join().contains("Blocked"));
    }

    @Test
    void writeFileBlocksOutsideWorkspace() {
        DefaultToolExecutor executor = newExecutor();
        CompletableFuture<String> result = executor.execute(
                "write_file",
                Map.of("file_path", "/etc/test-blocked.txt", "content", "x")
        );
        assertTrue(result.join().contains("Blocked"));
    }

    private DefaultToolExecutor newExecutor() {
        Safety.FileSystemSandbox sandbox = new Safety.FileSystemSandbox(
                List.of(workspace.toString(), System.getProperty("java.io.tmpdir"))
        );
        return new DefaultToolExecutor(
                workspace,
                sandbox,
                new com.aicode.agent.TaskManager(),
                new com.aicode.agent.Context.Scratchpad()
        );
    }
}

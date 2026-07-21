package com.aicode.app.application;

import com.aicode.agent.Safety;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves paths relative to the workspace and validates them against the filesystem sandbox.
 */
public final class WorkspaceGuard {
    private final Path workspace;
    private final Safety.FileSystemSandbox sandbox;

    public WorkspaceGuard(Path workspace, Safety.FileSystemSandbox sandbox) {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.sandbox = sandbox;
    }

    public Path workspace() {
        return workspace;
    }

    public Path resolve(String pathInput) {
        Path path = Path.of(pathInput);
        if (!path.isAbsolute()) {
            path = workspace.resolve(path);
        }
        return path.toAbsolutePath().normalize();
    }

    /**
     * @return null if allowed, otherwise a block message for the agent/UI
     */
    public String validate(String pathInput) {
        Path resolved = resolve(pathInput);
        String blocked = sandbox.check(resolved.toString());
        if (blocked != null) {
            return blocked + " Workspace: " + workspace;
        }
        return null;
    }

    /**
     * Editor open/save: only enforce workspace boundary, not agent sensitive-file patterns.
     *
     * @return null if allowed, otherwise a block message
     */
    public String validateForEditor(String pathInput) {
        Path resolved = resolve(pathInput);
        String blocked = sandbox.checkBoundary(resolved.toString());
        if (blocked != null) {
            return blocked + " Workspace: " + workspace;
        }
        return null;
    }

    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }
}

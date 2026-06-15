package com.aicode.app.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AgentsMdSetup {
    public static final String FILE_NAME = "AGENTS.md";

    private AgentsMdSetup() {}

    public static boolean hasAgentsMd(Path workspace) {
        return workspace != null && Files.isRegularFile(workspace.resolve(FILE_NAME));
    }

    public static Path agentsMdPath(Path workspace) {
        return workspace.resolve(FILE_NAME);
    }

    public static void writeTemplate(Path workspace, AgentsMdTemplate template) throws IOException {
        if (workspace == null) {
            throw new IllegalArgumentException("workspace is required");
        }
        if (template == null) {
            throw new IllegalArgumentException("template is required");
        }
        Path target = agentsMdPath(workspace);
        if (Files.exists(target)) {
            throw new IOException("文件已存在: " + target);
        }
        Files.writeString(target, template.content());
    }
}

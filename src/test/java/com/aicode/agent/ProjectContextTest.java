package com.aicode.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProjectContextTest {
    @TempDir
    Path workspace;

    @Test
    void loadsAgentsMd() throws Exception {
        Files.writeString(workspace.resolve("AGENTS.md"), "# Agent Guide\nUse Java 21.");
        String ctx = ProjectContext.loadForPrompt(workspace);
        assertNotNull(ctx);
        assertTrue(ctx.contains("AGENTS.md"));
        assertTrue(ctx.contains("Java 21"));
    }

    @Test
    void doesNotLoadProjectRules() throws Exception {
        Path rules = workspace.resolve(".aicode/rules");
        Files.createDirectories(rules);
        Files.writeString(rules.resolve("style.md"), "- Use 4 spaces");
        assertNull(ProjectContext.loadForPrompt(workspace));
    }

    @Test
    void prefersAgentsOverClaudeWhenBothPresent() throws Exception {
        Files.writeString(workspace.resolve("AGENTS.md"), "from agents");
        Files.writeString(workspace.resolve("CLAUDE.md"), "from claude");
        String ctx = ProjectContext.loadForPrompt(workspace);
        assertNotNull(ctx);
        int agentsIdx = ctx.indexOf("from agents");
        int claudeIdx = ctx.indexOf("from claude");
        assertTrue(agentsIdx >= 0 && claudeIdx >= 0);
        assertTrue(agentsIdx < claudeIdx);
    }

    @Test
    void emptyWorkspaceReturnsNull() {
        assertNull(ProjectContext.loadForPrompt(workspace));
    }

    @Test
    void doesNotLoadReadmeByDefault() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# Project\nBuild with Maven.");
        assertNull(ProjectContext.loadForPrompt(workspace));
    }

    @Test
    void skillsAreNotLoadedIntoProjectContext() throws Exception {
        Path skillDir = workspace.resolve(".cursor/skills/commit");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "Always write conventional commits.");
        assertNull(ProjectContext.loadForPrompt(workspace));
    }
}

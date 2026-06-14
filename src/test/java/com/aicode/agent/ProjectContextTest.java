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
    void loadsProjectRules() throws Exception {
        Path rules = workspace.resolve(".aicode/rules");
        Files.createDirectories(rules);
        Files.writeString(rules.resolve("style.md"), "- Use 4 spaces");
        String ctx = ProjectContext.loadForPrompt(workspace);
        assertNotNull(ctx);
        assertTrue(ctx.contains("Project Rules"));
        assertTrue(ctx.contains("4 spaces"));
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
    void loadsReadme() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "# Project\nBuild with Maven.");
        String ctx = ProjectContext.loadForPrompt(workspace);
        assertNotNull(ctx);
        assertTrue(ctx.contains("README.md"));
        assertTrue(ctx.contains("Maven"));
    }

    @Test
    void loadsMdcRulesWithFrontmatter() throws Exception {
        Path rules = workspace.resolve(".cursor/rules");
        Files.createDirectories(rules);
        Files.writeString(rules.resolve("java.mdc"), """
                ---
                description: Java conventions
                globs: **/*.java
                ---
                Prefer var for locals.
                """);
        String ctx = ProjectContext.loadForPrompt(workspace);
        assertNotNull(ctx);
        assertTrue(ctx.contains("Java conventions"));
        assertTrue(ctx.contains("Prefer var for locals"));
        assertFalse(ctx.contains("---"));
    }

    @Test
    void loadsSkills() throws Exception {
        Path skillDir = workspace.resolve(".cursor/skills/commit");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), "Always write conventional commits.");
        String ctx = ProjectContext.loadForPrompt(workspace);
        assertNotNull(ctx);
        assertTrue(ctx.contains("Skills"));
        assertTrue(ctx.contains("conventional commits"));
    }
}

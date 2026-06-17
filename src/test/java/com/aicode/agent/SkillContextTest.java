package com.aicode.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkillContextTest {
    @TempDir
    Path workspace;

    @Test
    void discoversProjectSkillMetadataWithoutInjectingBodyIntoCatalog() throws Exception {
        Path skillDir = workspace.resolve(".cursor/skills/commit");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: commit
                description: Generate conventional commits from git diffs.
                ---
                Always write conventional commits.
                """);

        var skills = SkillContext.discover(workspace);
        assertEquals(1, skills.size());
        assertEquals("commit", skills.getFirst().name());
        assertEquals("Generate conventional commits from git diffs.", skills.getFirst().description());
        assertEquals(".cursor/skills/commit/SKILL.md", skills.getFirst().readPath());

        String catalog = SkillContext.formatCatalog(skills);
        assertNotNull(catalog);
        assertTrue(catalog.contains("| commit |"));
        assertTrue(catalog.contains(".cursor/skills/commit/SKILL.md"));
        assertFalse(catalog.contains("Always write conventional commits"));
    }

    @Test
    void projectSkillOverridesUserSkillWithSameName(@TempDir Path userSkills) throws Exception {
        Path userSkill = userSkills.resolve("shared");
        Files.createDirectories(userSkill);
        Files.writeString(userSkill.resolve("SKILL.md"), """
                ---
                name: shared
                description: from user
                ---
                user body
                """);

        Path projectSkill = workspace.resolve(".aicode/skills/shared");
        Files.createDirectories(projectSkill);
        Files.writeString(projectSkill.resolve("SKILL.md"), """
                ---
                name: shared
                description: from project
                ---
                project body
                """);

        var skills = SkillContext.discover(workspace, userSkills, userSkills.resolve("unused"));
        assertEquals(1, skills.size());
        assertEquals("from project", skills.getFirst().description());
        assertEquals("project", skills.getFirst().scope());
    }

    @Test
    void alwaysApplySkillBodyIsFormattedSeparately() throws Exception {
        Path skillDir = workspace.resolve(".aicode/skills/style");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: style
                description: Team style guide
                alwaysApply: true
                ---
                Use 4 spaces for indentation.
                """);

        var skills = SkillContext.discover(workspace);
        assertNull(SkillContext.formatCatalog(skills));
        String alwaysApply = SkillContext.formatAlwaysApply(skills);
        assertNotNull(alwaysApply);
        assertTrue(alwaysApply.contains("Use 4 spaces"));
    }

    @Test
    void explicitOnlySkillIsMarkedInCatalog() throws Exception {
        Path skillDir = workspace.resolve(".cursor/skills/manual");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: manual
                description: Manual workflow
                disable-model-invocation: true
                ---
                Steps here
                """);

        String catalog = SkillContext.formatCatalog(SkillContext.discover(workspace));
        assertNotNull(catalog);
        assertTrue(catalog.contains("(explicit only)"));
    }
}

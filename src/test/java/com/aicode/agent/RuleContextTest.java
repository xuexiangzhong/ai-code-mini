package com.aicode.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RuleContextTest {
    @TempDir
    Path workspace;

    @Test
    void plainRuleWithoutFrontmatterIsAlwaysApplied() throws Exception {
        Path rules = workspace.resolve(".aicode/rules");
        Files.createDirectories(rules);
        Files.writeString(rules.resolve("style.md"), "- Use 4 spaces");

        var rulesMeta = RuleContext.discover(workspace);
        assertEquals(1, rulesMeta.size());
        assertEquals(RuleFileParser.RuleKind.ALWAYS_APPLY, rulesMeta.getFirst().kind());

        String alwaysApply = RuleContext.formatAlwaysApply(rulesMeta);
        assertNotNull(alwaysApply);
        assertTrue(alwaysApply.contains("4 spaces"));
        assertNull(RuleContext.formatCatalog(rulesMeta));
    }

    @Test
    void globRuleIsInjectedOnlyForMatchingActiveFile(@TempDir Path userRules) throws Exception {
        Path rules = workspace.resolve(".cursor/rules");
        Files.createDirectories(rules);
        Files.writeString(rules.resolve("java.mdc"), """
                ---
                description: Java conventions
                globs: **/*.java
                ---
                Prefer var for locals.
                """);

        var discovered = RuleContext.discover(workspace, userRules, userRules.resolve("unused"));
        assertEquals(1, discovered.size());
        assertEquals(RuleFileParser.RuleKind.GLOB, discovered.getFirst().kind());
        assertNull(RuleContext.formatAlwaysApply(discovered));

        Path javaFile = workspace.resolve("src/Foo.java");
        Files.createDirectories(javaFile.getParent());
        Files.writeString(javaFile, "class Foo {}");

        String matching = RuleContext.formatMatchingGlobRules(workspace, javaFile, discovered);
        assertNotNull(matching);
        assertTrue(matching.contains("Prefer var for locals"));

        Path other = workspace.resolve("README.txt");
        Files.writeString(other, "hello");
        assertNull(RuleContext.formatMatchingGlobRules(workspace, other, discovered));
    }

    @Test
    void descriptionOnlyRuleAppearsInCatalog() throws Exception {
        Path rules = workspace.resolve(".aicode/rules");
        Files.createDirectories(rules);
        Files.writeString(rules.resolve("review.mdc"), """
                ---
                description: Review pull requests using team standards
                alwaysApply: false
                ---
                Hidden review body
                """);

        var discovered = RuleContext.discover(workspace);
        assertEquals(RuleFileParser.RuleKind.AGENT_REQUESTED, discovered.getFirst().kind());
        assertNull(RuleContext.formatAlwaysApply(discovered));

        String catalog = RuleContext.formatCatalog(discovered);
        assertNotNull(catalog);
        assertTrue(catalog.contains("Review pull requests"));
        assertTrue(catalog.contains("review.mdc"));
        assertFalse(catalog.contains("Hidden review body"));
    }

    @Test
    void projectRuleOverridesUserRuleWithSameId(@TempDir Path userRules) throws Exception {
        Files.createDirectories(userRules);
        Files.writeString(userRules.resolve("shared.md"), "from user");

        Path projectRules = workspace.resolve(".aicode/rules");
        Files.createDirectories(projectRules);
        Files.writeString(projectRules.resolve("shared.md"), "from project");

        var discovered = RuleContext.discover(workspace, userRules, userRules.resolve("unused"));
        assertEquals(1, discovered.size());
        assertEquals("project", discovered.getFirst().scope());
        assertTrue(RuleContext.formatAlwaysApply(discovered).contains("from project"));
    }

    @Test
    void matchesGlobPatterns() {
        Path root = Path.of("/project");
        Path javaFile = Path.of("/project/src/Foo.java");
        assertTrue(RuleContext.matchesGlob(root, javaFile, "**/*.java"));
        assertFalse(RuleContext.matchesGlob(root, Path.of("/project/README.md"), "**/*.java"));
    }
}

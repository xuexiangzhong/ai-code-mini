package com.aicode.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuleFileParserTest {
    @Test
    void stripsMdcFrontmatter() {
        String raw = """
                ---
                description: Java style
                globs: **/*.java
                alwaysApply: true
                ---
                Use 4 spaces for indentation.
                """;
        RuleFileParser.ParsedRule parsed = RuleFileParser.parse(raw);
        assertTrue(parsed.metadata().contains("Java style"));
        assertTrue(parsed.metadata().contains("**/*.java"));
        assertEquals("Use 4 spaces for indentation.", parsed.body());
    }

    @Test
    void plainMarkdownUnchanged() {
        RuleFileParser.ParsedRule parsed = RuleFileParser.parse("# Title\nBody");
        assertEquals("", parsed.metadata());
        assertEquals("# Title\nBody", parsed.body());
    }

    @Test
    void parsesSkillFrontmatter() {
        RuleFileParser.ParsedSkill parsed = RuleFileParser.parseSkill("""
                ---
                name: commit
                description: Generate conventional commits
                disable-model-invocation: false
                alwaysApply: true
                ---
                Step one
                """);
        assertEquals("commit", parsed.name());
        assertEquals("Generate conventional commits", parsed.description());
        assertFalse(parsed.disableModelInvocation());
        assertTrue(parsed.alwaysApply());
        assertEquals("Step one", parsed.body());
    }

    @Test
    void parsesRuleFileKinds() {
        RuleFileParser.ParsedRuleFile legacy = RuleFileParser.parseRuleFile("- Use 4 spaces");
        assertTrue(legacy.alwaysApply());
        assertEquals(RuleFileParser.RuleKind.ALWAYS_APPLY, legacy.kind());

        RuleFileParser.ParsedRuleFile glob = RuleFileParser.parseRuleFile("""
                ---
                description: Java conventions
                globs: **/*.java
                ---
                Prefer var
                """);
        assertFalse(glob.alwaysApply());
        assertEquals(RuleFileParser.RuleKind.GLOB, glob.kind());

        RuleFileParser.ParsedRuleFile requested = RuleFileParser.parseRuleFile("""
                ---
                description: Review PRs
                alwaysApply: false
                ---
                Body
                """);
        assertEquals(RuleFileParser.RuleKind.AGENT_REQUESTED, requested.kind());
    }
}

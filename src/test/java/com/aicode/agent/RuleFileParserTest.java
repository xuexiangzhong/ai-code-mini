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
}

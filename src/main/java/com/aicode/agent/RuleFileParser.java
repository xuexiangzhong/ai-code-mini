package com.aicode.agent;

/**
 * Parses Cursor-style {@code .mdc} rule files with YAML frontmatter.
 */
public final class RuleFileParser {
    public record ParsedRule(String metadata, String body) {}

    private RuleFileParser() {}

    public static ParsedRule parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ParsedRule("", "");
        }
        String text = raw.strip();
        if (!text.startsWith("---")) {
            return new ParsedRule("", text);
        }
        int end = text.indexOf("\n---", 3);
        if (end < 0) {
            return new ParsedRule("", text);
        }
        String frontmatter = text.substring(3, end).strip();
        String body = text.substring(end + 4).strip();
        return new ParsedRule(parseFrontmatter(frontmatter), body);
    }

    private static String parseFrontmatter(String yaml) {
        StringBuilder notes = new StringBuilder();
        for (String line : yaml.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("description:")) {
                appendNote(notes, "Description", trimmed.substring("description:".length()).strip());
            } else if (trimmed.startsWith("globs:")) {
                appendNote(notes, "Applies to", trimmed.substring("globs:".length()).strip());
            } else if (trimmed.startsWith("alwaysApply:")) {
                appendNote(notes, "Always apply", trimmed.substring("alwaysApply:".length()).strip());
            }
        }
        return notes.toString().strip();
    }

    private static void appendNote(StringBuilder notes, String label, String value) {
        if (value.isBlank()) {
            return;
        }
        if (!notes.isEmpty()) {
            notes.append('\n');
        }
        notes.append('-').append(' ').append(label).append(": ").append(value);
    }
}

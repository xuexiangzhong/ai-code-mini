package com.aicode.agent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses Cursor-style {@code .mdc} rule files with YAML frontmatter.
 */
public final class RuleFileParser {
    public record ParsedRule(String metadata, String body) {}

    public record ParsedRuleFile(
            String description,
            String globs,
            boolean alwaysApply,
            boolean hasFrontmatter,
            String body
    ) {
        public RuleKind kind() {
            if (alwaysApply) {
                return RuleKind.ALWAYS_APPLY;
            }
            if (!globs.isBlank()) {
                return RuleKind.GLOB;
            }
            if (!description.isBlank()) {
                return RuleKind.AGENT_REQUESTED;
            }
            if (!hasFrontmatter) {
                return RuleKind.ALWAYS_APPLY;
            }
            return RuleKind.ALWAYS_APPLY;
        }
    }

    public enum RuleKind {
        ALWAYS_APPLY,
        GLOB,
        AGENT_REQUESTED
    }

    public record ParsedSkill(
            String name,
            String description,
            boolean disableModelInvocation,
            boolean alwaysApply,
            String body
    ) {}

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

    public static ParsedSkill parseSkill(String raw) {
        ParsedRule base = parse(raw);
        Map<String, String> fields = extractYamlFields(raw);
        String name = fields.getOrDefault("name", "").strip();
        String description = fields.getOrDefault("description", "").strip();
        if (description.isBlank()) {
            description = descriptionFromMetadata(base.metadata());
        }
        boolean disableModelInvocation = parseBool(fields.get("disable-model-invocation"), true);
        boolean alwaysApply = parseBool(fields.get("alwaysApply"), false);
        return new ParsedSkill(name, description, disableModelInvocation, alwaysApply, base.body());
    }

    public static ParsedRuleFile parseRuleFile(String raw) {
        ParsedRule base = parse(raw);
        Map<String, String> fields = extractYamlFields(raw);
        boolean hasFrontmatter = hasYamlFrontmatter(raw);

        String description = fields.getOrDefault("description", "").strip();
        if (description.isBlank()) {
            description = descriptionFromMetadata(base.metadata());
        }

        String globs = fields.getOrDefault("globs", "").strip();

        boolean alwaysApply;
        if (!hasFrontmatter) {
            alwaysApply = true;
        } else if (fields.containsKey("alwaysApply")) {
            alwaysApply = parseBool(fields.get("alwaysApply"), false);
        } else {
            alwaysApply = false;
        }

        return new ParsedRuleFile(description, globs, alwaysApply, hasFrontmatter, base.body());
    }

    private static boolean hasYamlFrontmatter(String raw) {
        return raw != null && raw.strip().startsWith("---");
    }

    private static String descriptionFromMetadata(String metadata) {
        if (metadata.isBlank()) {
            return "";
        }
        for (String line : metadata.split("\n")) {
            if (line.startsWith("- Description:")) {
                return line.substring("- Description:".length()).strip();
            }
        }
        return "";
    }

    static Map<String, String> extractYamlFields(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        String text = raw.strip();
        if (!text.startsWith("---")) {
            return Map.of();
        }
        int end = text.indexOf("\n---", 3);
        if (end < 0) {
            return Map.of();
        }
        String yaml = text.substring(3, end);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : yaml.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).strip();
            String value = line.substring(colon + 1).strip();
            fields.put(key, stripQuotes(value));
        }
        return fields;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean parseBool(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes");
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

package com.aicode.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Loads project-level instructions for the Agent system prompt:
 * AGENTS.md and CLAUDE.md. Rules are handled by {@link RuleContext}.
 */
public final class ProjectContext {
    private static final int MAX_SECTION_CHARS = 12_000;
    private static final int MAX_TOTAL_CHARS = 24_000;

    private ProjectContext() {}

    public static int defaultMaxTotal() {
        return MAX_TOTAL_CHARS;
    }

    public static String loadForPrompt(Path workspace) {
        return loadForPrompt(workspace, MAX_TOTAL_CHARS);
    }

    public static String loadForPrompt(Path workspace, int maxTotalChars) {
        List<Section> sections = new ArrayList<>();

        readSingleFile(workspace, "AGENTS.md", "AGENTS.md", 95).ifPresent(sections::add);
        readSingleFile(workspace, "CLAUDE.md", "CLAUDE.md", 90).ifPresent(sections::add);
        readSingleFile(workspace, ".claude/CLAUDE.md", "CLAUDE.md", 90).ifPresent(sections::add);

        if (sections.isEmpty()) {
            return null;
        }

        sections.sort(Comparator.comparingInt(Section::priority).reversed());
        StringBuilder sb = new StringBuilder();
        int total = 0;
        int budget = Math.max(1_000, maxTotalChars);
        for (Section section : sections) {
            String block = "## " + section.title() + "\n" + section.content();
            if (total + block.length() + 2 > budget && !sb.isEmpty()) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(block);
            total += block.length() + 2;
        }
        return sb.toString();
    }

    private record Section(String title, String content, int priority) {}

    private static java.util.Optional<Section> readSingleFile(
            Path workspace, String relative, String title, int priority
    ) {
        Path file = workspace.resolve(relative);
        if (!Files.isRegularFile(file)) {
            return java.util.Optional.empty();
        }
        try {
            RuleFileParser.ParsedRule parsed = RuleFileParser.parse(Files.readString(file).strip());
            String content = formatRuleBody(parsed);
            content = truncate(content, MAX_SECTION_CHARS);
            if (content.isBlank()) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new Section(title, content, priority));
        } catch (IOException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static String formatRuleBody(RuleFileParser.ParsedRule parsed) {
        if (parsed.metadata().isBlank()) {
            return parsed.body();
        }
        if (parsed.body().isBlank()) {
            return parsed.metadata();
        }
        return parsed.metadata() + "\n\n" + parsed.body();
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n…(truncated)";
    }
}

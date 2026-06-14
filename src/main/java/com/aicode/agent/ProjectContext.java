package com.aicode.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads project-level instructions for the Agent system prompt:
 * AGENTS.md, CLAUDE.md, README.md, skills, and rules from
 * {@code .aicode/rules} / {@code .cursor/rules}.
 */
public final class ProjectContext {
    private static final int MAX_SECTION_CHARS = 12_000;
    private static final int MAX_TOTAL_CHARS = 24_000;
    private static final int MAX_SKILL_CHARS = 3_000;

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
        readSingleFile(workspace, "README.md", "README.md", 88).ifPresent(sections::add);
        readRulesDir(workspace, ".aicode/rules", "Project Rules (.aicode/rules)", 85).ifPresent(sections::add);
        readRulesDir(workspace, ".cursor/rules", "Project Rules (.cursor/rules)", 84).ifPresent(sections::add);
        readSkillsDir(workspace, ".aicode/skills", "Skills (.aicode/skills)", 83).ifPresent(sections::add);
        readSkillsDir(workspace, ".cursor/skills", "Skills (.cursor/skills)", 82).ifPresent(sections::add);

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

    private static java.util.Optional<Section> readRulesDir(
            Path workspace, String relativeDir, String title, int priority
    ) {
        Path dir = workspace.resolve(relativeDir);
        if (!Files.isDirectory(dir)) {
            return java.util.Optional.empty();
        }

        List<Path> ruleFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(ProjectContext::isRuleFile)
                    .sorted()
                    .forEach(ruleFiles::add);
        } catch (IOException ignored) {
            return java.util.Optional.empty();
        }

        if (ruleFiles.isEmpty()) {
            return java.util.Optional.empty();
        }

        StringBuilder content = new StringBuilder();
        for (Path ruleFile : ruleFiles) {
            try {
                String rel = workspace.relativize(ruleFile).toString().replace('\\', '/');
                RuleFileParser.ParsedRule parsed = RuleFileParser.parse(Files.readString(ruleFile).strip());
                if (parsed.body().isBlank() && parsed.metadata().isBlank()) {
                    continue;
                }
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                content.append("### ").append(rel).append('\n');
                if (!parsed.metadata().isBlank()) {
                    content.append(parsed.metadata()).append("\n\n");
                }
                content.append(truncate(parsed.body(), 4000));
            } catch (IOException ignored) {
                // skip unreadable rule file
            }
        }

        if (content.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Section(title, truncate(content.toString(), MAX_SECTION_CHARS), priority));
    }

    private static java.util.Optional<Section> readSkillsDir(
            Path workspace, String relativeDir, String title, int priority
    ) {
        Path dir = workspace.resolve(relativeDir);
        if (!Files.isDirectory(dir)) {
            return java.util.Optional.empty();
        }

        List<Path> skillFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> "SKILL.md".equalsIgnoreCase(p.getFileName().toString()))
                    .sorted()
                    .forEach(skillFiles::add);
        } catch (IOException ignored) {
            return java.util.Optional.empty();
        }

        if (skillFiles.isEmpty()) {
            return java.util.Optional.empty();
        }

        StringBuilder content = new StringBuilder();
        for (Path skillFile : skillFiles) {
            try {
                Path skillDir = skillFile.getParent();
                String name = skillDir != null ? skillDir.getFileName().toString() : skillFile.toString();
                String body = Files.readString(skillFile).strip();
                if (body.isBlank()) {
                    continue;
                }
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                content.append("### Skill: ").append(name).append('\n');
                content.append(truncate(body, MAX_SKILL_CHARS));
            } catch (IOException ignored) {
                // skip unreadable skill
            }
        }

        if (content.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Section(title, truncate(content.toString(), MAX_SECTION_CHARS), priority));
    }

    private static boolean isRuleFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".mdc") || name.endsWith(".txt");
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

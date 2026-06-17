package com.aicode.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Discovers Agent Skills (Cursor-style {@code SKILL.md} directories) and formats
 * metadata catalogs for the system prompt. Full skill bodies are loaded on demand
 * via {@code read_file}, not pre-injected.
 */
public final class SkillContext {
    private static final int MAX_ALWAYS_APPLY_CHARS = 4_000;

    public record SkillMeta(
            String name,
            String description,
            Path skillFile,
            String readPath,
            String scope,
            boolean disableModelInvocation,
            boolean alwaysApply,
            String body
    ) {}

    private SkillContext() {}

    public static Path userAicodeSkillsDir() {
        return Path.of(System.getProperty("user.home"), ".aicode", "skills");
    }

    public static Path userCursorSkillsDir() {
        return Path.of(System.getProperty("user.home"), ".cursor", "skills");
    }

    /** Roots allowed for {@code read_file} so personal skills remain accessible. */
    public static List<String> sandboxAllowedRoots() {
        List<String> roots = new ArrayList<>();
        roots.add(userAicodeSkillsDir().toString());
        roots.add(userCursorSkillsDir().toString());
        return roots;
    }

    public static List<String> toolSandboxRoots(Path workspace) {
        List<String> roots = new ArrayList<>();
        roots.add(workspace.toAbsolutePath().normalize().toString());
        roots.add(System.getProperty("java.io.tmpdir"));
        roots.addAll(sandboxAllowedRoots());
        roots.addAll(RuleContext.sandboxAllowedRoots());
        return roots;
    }

    public static List<SkillMeta> discover(Path workspace) {
        return discover(workspace, userCursorSkillsDir(), userAicodeSkillsDir());
    }

    static List<SkillMeta> discover(Path workspace, Path userCursorRoot, Path userAicodeRoot) {
        Path root = workspace.toAbsolutePath().normalize();
        Map<String, SkillMeta> byName = new LinkedHashMap<>();
        collectSkills(userCursorRoot, "user", root, byName);
        collectSkills(userAicodeRoot, "user", root, byName);
        collectSkills(root.resolve(".cursor/skills"), "project", root, byName);
        collectSkills(root.resolve(".aicode/skills"), "project", root, byName);
        return List.copyOf(byName.values());
    }

    public static String formatCatalog(List<SkillMeta> skills) {
        List<SkillMeta> catalog = skills.stream().filter(skill -> !skill.alwaysApply()).toList();
        if (catalog.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("""
                Agent Skills are task-specific instruction files. When a task matches a skill's \
                description (or the user names a skill), read its SKILL.md with read_file BEFORE \
                acting. Follow the skill immediately; do not only mention it.
                Skills marked (explicit only) apply only when the user names them directly.

                """);
        sb.append("| Name | Description | Path |\n");
        sb.append("|------|-------------|------|\n");
        for (SkillMeta skill : catalog) {
            String description = skill.description().isBlank()
                    ? "(no description)"
                    : skill.description();
            if (skill.disableModelInvocation()) {
                description = description + " (explicit only)";
            }
            sb.append("| ")
                    .append(escapeTable(skill.name()))
                    .append(" | ")
                    .append(escapeTable(description))
                    .append(" | ")
                    .append(escapeTable(skill.readPath()))
                    .append(" |\n");
        }
        return sb.toString().strip();
    }

    public static String formatAlwaysApply(List<SkillMeta> skills) {
        List<SkillMeta> applied = skills.stream().filter(SkillMeta::alwaysApply).toList();
        if (applied.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (SkillMeta skill : applied) {
            sb.append("### Skill: ").append(skill.name()).append('\n');
            if (!skill.description().isBlank()) {
                sb.append(skill.description()).append("\n\n");
            }
            if (!skill.body().isBlank()) {
                sb.append(truncate(skill.body(), MAX_ALWAYS_APPLY_CHARS)).append("\n\n");
            }
        }
        String text = sb.toString().strip();
        return text.isBlank() ? null : text;
    }

    private static void collectSkills(
            Path skillsRoot, String scope, Path workspace, Map<String, SkillMeta> byName
    ) {
        if (!Files.isDirectory(skillsRoot)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(skillsRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> "SKILL.md".equalsIgnoreCase(path.getFileName().toString()))
                    .sorted()
                    .forEach(skillFile -> {
                        SkillMeta meta = parseSkillFile(skillFile, scope, workspace);
                        if (meta != null) {
                            byName.put(meta.name(), meta);
                        }
                    });
        } catch (IOException ignored) {
            // skip unreadable skill tree
        }
    }

    private static SkillMeta parseSkillFile(Path skillFile, String scope, Path workspace) {
        try {
            RuleFileParser.ParsedSkill parsed = RuleFileParser.parseSkill(Files.readString(skillFile));
            Path skillDir = skillFile.getParent();
            String dirName = skillDir != null ? skillDir.getFileName().toString() : "skill";
            String name = parsed.name().isBlank() ? dirName : parsed.name();
            String readPath = "project".equals(scope)
                    ? workspace.relativize(skillFile.toAbsolutePath().normalize()).toString().replace('\\', '/')
                    : skillFile.toAbsolutePath().normalize().toString();
            return new SkillMeta(
                    name,
                    parsed.description(),
                    skillFile.toAbsolutePath().normalize(),
                    readPath,
                    scope,
                    parsed.disableModelInvocation(),
                    parsed.alwaysApply(),
                    parsed.body()
            );
        } catch (IOException ignored) {
            return null;
        }
    }

    private static String escapeTable(String value) {
        return value.replace("|", "\\|");
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n…(truncated)";
    }
}

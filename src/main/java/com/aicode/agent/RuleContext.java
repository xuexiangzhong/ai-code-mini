package com.aicode.agent;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Discovers Cursor-style project and user rules, classifies them by
 * {@code alwaysApply} / {@code globs} / description, and formats prompt sections.
 * Rule bodies are not all pre-injected: only {@code alwaysApply} rules go into the
 * system prompt; glob rules attach per-turn; agent-requested rules use a catalog.
 */
public final class RuleContext {
    private static final int MAX_ALWAYS_APPLY_CHARS = 4_000;
    private static final int MAX_GLOB_RULE_CHARS = 3_000;

    public record RuleMeta(
            String id,
            String description,
            String globs,
            Path ruleFile,
            String readPath,
            String scope,
            RuleFileParser.RuleKind kind,
            String body
    ) {}

    private RuleContext() {}

    public static Path userAicodeRulesDir() {
        return Path.of(System.getProperty("user.home"), ".aicode", "rules");
    }

    public static Path userCursorRulesDir() {
        return Path.of(System.getProperty("user.home"), ".cursor", "rules");
    }

    public static List<String> sandboxAllowedRoots() {
        List<String> roots = new ArrayList<>();
        roots.add(userAicodeRulesDir().toString());
        roots.add(userCursorRulesDir().toString());
        return roots;
    }

    public static List<RuleMeta> discover(Path workspace) {
        return discover(workspace, userCursorRulesDir(), userAicodeRulesDir());
    }

    static List<RuleMeta> discover(Path workspace, Path userCursorRoot, Path userAicodeRoot) {
        Path root = workspace.toAbsolutePath().normalize();
        Map<String, RuleMeta> byId = new LinkedHashMap<>();
        collectRules(userCursorRoot, "user", root, byId);
        collectRules(userAicodeRoot, "user", root, byId);
        collectRules(root.resolve(".cursor/rules"), "project", root, byId);
        collectRules(root.resolve(".aicode/rules"), "project", root, byId);
        return List.copyOf(byId.values());
    }

    public static List<RuleMeta> forScope(List<RuleMeta> rules, String scope) {
        return rules.stream().filter(rule -> scope.equals(rule.scope())).toList();
    }

    public static String formatAlwaysApply(List<RuleMeta> rules) {
        List<RuleMeta> applied = rules.stream()
                .filter(rule -> rule.kind() == RuleFileParser.RuleKind.ALWAYS_APPLY)
                .toList();
        if (applied.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (RuleMeta rule : applied) {
            sb.append("### ").append(rule.id()).append('\n');
            if (!rule.description().isBlank()) {
                sb.append(rule.description()).append("\n\n");
            }
            if (!rule.body().isBlank()) {
                sb.append(truncate(rule.body(), MAX_ALWAYS_APPLY_CHARS)).append("\n\n");
            }
        }
        String text = sb.toString().strip();
        return text.isBlank() ? null : text;
    }

    public static String formatCatalog(List<RuleMeta> rules) {
        List<RuleMeta> catalog = rules.stream()
                .filter(rule -> rule.kind() == RuleFileParser.RuleKind.AGENT_REQUESTED)
                .toList();
        if (catalog.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("""
                Project rules with descriptions are loaded on demand. When a task matches a rule's \
                description, read the rule file with read_file BEFORE acting.

                """);
        sb.append("| Rule | Description | Path |\n");
        sb.append("|------|-------------|------|\n");
        for (RuleMeta rule : catalog) {
            String description = rule.description().isBlank()
                    ? "(no description)"
                    : rule.description();
            sb.append("| ")
                    .append(escapeTable(rule.id()))
                    .append(" | ")
                    .append(escapeTable(description))
                    .append(" | ")
                    .append(escapeTable(rule.readPath()))
                    .append(" |\n");
        }
        return sb.toString().strip();
    }

    public static String formatMatchingGlobRules(Path workspace, Path activeFile, List<RuleMeta> rules) {
        if (activeFile == null || rules == null || rules.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (RuleMeta rule : rules) {
            if (rule.kind() != RuleFileParser.RuleKind.GLOB) {
                continue;
            }
            if (!matchesGlob(workspace, activeFile, rule.globs())) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("### ").append(rule.id()).append('\n');
            if (!rule.globs().isBlank()) {
                sb.append("Applies to: ").append(rule.globs()).append("\n\n");
            }
            if (!rule.body().isBlank()) {
                sb.append(truncate(rule.body(), MAX_GLOB_RULE_CHARS));
            }
        }
        return sb.isEmpty() ? null : sb.toString().strip();
    }

    public static boolean matchesGlob(Path workspace, Path file, String globsPattern) {
        if (globsPattern == null || globsPattern.isBlank() || file == null || workspace == null) {
            return false;
        }
        Path workspaceRoot = workspace.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        String relative;
        try {
            relative = workspaceRoot.relativize(normalizedFile).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (relative.startsWith("..")) {
            return false;
        }

        Path relativePath = Path.of(relative);
        for (String pattern : globsPattern.split(",")) {
            String trimmed = pattern.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + trimmed);
            if (matcher.matches(relativePath)) {
                return true;
            }
        }
        return false;
    }

    private static void collectRules(
            Path rulesRoot, String scope, Path workspace, Map<String, RuleMeta> byId
    ) {
        if (!Files.isDirectory(rulesRoot)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(rulesRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(RuleContext::isRuleFile)
                    .sorted()
                    .forEach(ruleFile -> {
                        RuleMeta meta = parseRuleFile(ruleFile, rulesRoot, scope, workspace);
                        if (meta != null) {
                            byId.put(meta.id(), meta);
                        }
                    });
        } catch (IOException ignored) {
            // skip unreadable rule tree
        }
    }

    private static RuleMeta parseRuleFile(
            Path ruleFile, Path rulesRoot, String scope, Path workspace
    ) {
        try {
            RuleFileParser.ParsedRuleFile parsed = RuleFileParser.parseRuleFile(Files.readString(ruleFile));
            if (parsed.body().isBlank() && parsed.description().isBlank() && !parsed.alwaysApply()) {
                return null;
            }
            String id = rulesRoot.toAbsolutePath().normalize()
                    .relativize(ruleFile.toAbsolutePath().normalize())
                    .toString()
                    .replace('\\', '/');
            String readPath = "project".equals(scope)
                    ? workspace.relativize(ruleFile.toAbsolutePath().normalize()).toString().replace('\\', '/')
                    : ruleFile.toAbsolutePath().normalize().toString();
            return new RuleMeta(
                    id,
                    parsed.description(),
                    parsed.globs(),
                    ruleFile.toAbsolutePath().normalize(),
                    readPath,
                    scope,
                    parsed.kind(),
                    parsed.body()
            );
        } catch (IOException ignored) {
            return null;
        }
    }

    private static boolean isRuleFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".mdc") || name.endsWith(".txt");
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

package com.aicode.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Loads global user rules from {@code ~/.aicode/rules/}. */
public final class UserRulesContext {
    private static final int MAX_TOTAL_CHARS = 8_000;
    private static final int MAX_FILE_CHARS = 4_000;

    private UserRulesContext() {}

    public static Path userRulesDir() {
        return Path.of(System.getProperty("user.home"), ".aicode", "rules");
    }

    public static String loadForPrompt() {
        Path dir = userRulesDir();
        if (!Files.isDirectory(dir)) {
            return null;
        }

        List<Path> ruleFiles = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(UserRulesContext::isRuleFile)
                    .sorted()
                    .forEach(ruleFiles::add);
        } catch (IOException ignored) {
            return null;
        }

        if (ruleFiles.isEmpty()) {
            return null;
        }

        StringBuilder content = new StringBuilder();
        int total = 0;
        for (Path ruleFile : ruleFiles) {
            try {
                String rel = userRulesDir().relativize(ruleFile).toString().replace('\\', '/');
                RuleFileParser.ParsedRule parsed = RuleFileParser.parse(Files.readString(ruleFile).strip());
                if (parsed.body().isBlank() && parsed.metadata().isBlank()) {
                    continue;
                }
                StringBuilder block = new StringBuilder();
                block.append("### ").append(rel).append('\n');
                if (!parsed.metadata().isBlank()) {
                    block.append(parsed.metadata()).append("\n\n");
                }
                block.append(truncate(parsed.body(), MAX_FILE_CHARS));
                if (total + block.length() + 2 > MAX_TOTAL_CHARS && !content.isEmpty()) {
                    break;
                }
                if (!content.isEmpty()) {
                    content.append("\n\n");
                }
                content.append(block);
                total += block.length() + 2;
            } catch (IOException ignored) {
                // skip unreadable rule file
            }
        }

        return content.isEmpty() ? null : content.toString();
    }

    private static boolean isRuleFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".md") || name.endsWith(".mdc") || name.endsWith(".txt");
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "\n…(truncated)";
    }
}

package com.aicode.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Security and project awareness utilities.
 */
public final class Safety {
    private Safety() {}

    public static class FileSystemSandbox {
        private static final List<Pattern> DEFAULT_BLOCKED = List.of(
                Pattern.compile("\\.env($|\\.)"),
                Pattern.compile("/(\\.ssh|\\.gnupg)/"),
                Pattern.compile("/\\.git/config$"),
                Pattern.compile("/(passwd|shadow)$"),
                Pattern.compile("/credentials\\.json$"),
                Pattern.compile("/\\.aws/")
        );

        private final List<Path> allowedPaths;
        private final List<Pattern> blockedPatterns;

        public FileSystemSandbox(List<String> allowedPaths) {
            this(allowedPaths, List.of());
        }

        public FileSystemSandbox(List<String> allowedPaths, List<Pattern> extraBlocked) {
            this.allowedPaths = allowedPaths.stream()
                    .map(p -> Path.of(p).toAbsolutePath().normalize())
                    .toList();
            this.blockedPatterns = new ArrayList<>(DEFAULT_BLOCKED);
            this.blockedPatterns.addAll(extraBlocked);
        }

        public String check(String filePath) {
            Path resolved = Path.of(filePath).toAbsolutePath().normalize();
            String resolvedStr = normalizePathForMatching(resolved.toString());

            for (Pattern pattern : blockedPatterns) {
                if (pattern.matcher(resolvedStr).find()) {
                    return "Blocked: \"" + filePath + "\" matches a sensitive file pattern.";
                }
            }

            boolean inAllowed = allowedPaths.stream().anyMatch(allowed -> {
                if (resolved.equals(allowed)) {
                    return true;
                }
                return resolved.startsWith(allowed.resolve(""));
            });

            if (!inAllowed) {
                return "Blocked: \"" + filePath + "\" is outside allowed directories.";
            }
            return null;
        }

        public boolean isAllowed(String filePath) {
            return check(filePath) == null;
        }
    }

    private static final List<MapEntry> DANGEROUS_PATTERNS = List.of(
            new MapEntry(Pattern.compile("\\brm\\s+(-[rf]+\\s+|.*--no-preserve-root)"), "Recursive/forced file deletion"),
            new MapEntry(Pattern.compile("(?i)\\bRemove-Item\\b.*-Recurse"), "Recursive/forced file deletion"),
            new MapEntry(Pattern.compile("(?i)\\b(rd|rmdir)\\s+/[sq]"), "Recursive/forced file deletion"),
            new MapEntry(Pattern.compile("(?i)\\bdel\\s+/[fqs]"), "Recursive/forced file deletion"),
            new MapEntry(Pattern.compile("\\bgit\\s+push\\s+.*--force"), "Force push may overwrite remote history"),
            new MapEntry(Pattern.compile("\\bgit\\s+reset\\s+--hard"), "Hard reset discards uncommitted changes"),
            new MapEntry(Pattern.compile("\\bchmod\\s+777\\b"), "Sets world-writable permissions"),
            new MapEntry(Pattern.compile("\\bcurl\\s+.*\\|\\s*(sh|bash)\\b"), "Piping remote script to shell"),
            new MapEntry(Pattern.compile("(?i)\\bcurl\\s+.*\\|\\s*(iex|Invoke-Expression)\\b"), "Piping remote script to shell"),
            new MapEntry(Pattern.compile("\\bsudo\\s+"), "Elevated privilege execution"),
            new MapEntry(Pattern.compile("(?i)\\bFormat-Volume\\b"), "Disk formatting operation"),
            new MapEntry(Pattern.compile("\\b(DROP|DELETE\\s+FROM|TRUNCATE)\\b", Pattern.CASE_INSENSITIVE), "Destructive database operation"),
            new MapEntry(Pattern.compile("\\bkill\\s+-9\\b"), "Forceful process termination"),
            new MapEntry(Pattern.compile("(?i)\\bStop-Process\\b.*-Force"), "Forceful process termination")
    );

    /** Normalize path separators so blocked patterns work on Windows backslash paths. */
    static String normalizePathForMatching(String path) {
        return path.replace('\\', '/');
    }

    private record MapEntry(Pattern pattern, String reason) {}

    public static String checkDangerousCommand(String command) {
        for (MapEntry entry : DANGEROUS_PATTERNS) {
            if (entry.pattern().matcher(command).find()) {
                return entry.reason();
            }
        }
        return null;
    }

    /** @deprecated use {@link ProjectContext#loadForPrompt(java.nio.file.Path)} */
    @Deprecated
    public static String readProjectConfig(String projectDir) {
        return ProjectContext.loadForPrompt(Path.of(projectDir));
    }

    public record GitInfo(String branch, String lastCommit, String status, String remoteUrl) {}

    public static GitInfo parseGitInfo(String branch, String lastCommit, String status, String remoteUrl) {
        return new GitInfo(
                branch != null ? branch.strip() : "",
                lastCommit != null ? lastCommit.strip() : "",
                status != null ? status.strip() : "",
                remoteUrl != null ? remoteUrl.strip() : ""
        );
    }

    public static String formatGitContext(GitInfo info) {
        List<String> lines = new ArrayList<>();
        lines.add("## Project Context");
        if (!info.branch().isEmpty()) {
            lines.add("- Branch: " + info.branch());
        }
        if (!info.lastCommit().isEmpty()) {
            lines.add("- Last commit: " + info.lastCommit());
        }
        if (!info.remoteUrl().isEmpty()) {
            lines.add("- Remote: " + info.remoteUrl());
        }
        if (!info.status().isEmpty()) {
            lines.add("- Status:\n" + info.status());
        }
        return lines.size() > 1 ? String.join("\n", lines) : "";
    }
}

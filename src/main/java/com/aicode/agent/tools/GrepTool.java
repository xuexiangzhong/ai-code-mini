package com.aicode.agent.tools;

import com.aicode.agent.llm.Tool;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

public final class GrepTool {
    public static final Tool DEFINITION = new Tool(
            "grep",
            "Search file contents for a pattern (regex supported). "
                    + "Returns matching lines with file paths and line numbers.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "pattern", Map.of(
                                    "type", "string",
                                    "description", "The regex pattern to search for"
                            ),
                            "path", Map.of(
                                    "type", "string",
                                    "description", "File or directory to search in (default: current directory)"
                            ),
                            "include", Map.of(
                                    "type", "string",
                                    "description", "Glob filter for file names (e.g. \"*.ts\", \"*.py\")"
                            )
                    ),
                    "required", List.of("pattern")
            )
    );

    public static final int MAX_MATCHES = 100;
    public static final int MAX_FILE_SIZE = 512 * 1024;

    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "__pycache__", ".venv", "dist", "build",
            ".next", ".cache", "coverage", "target"
    );

    public record Input(String pattern, String path, String include) {
        public static Input fromMap(Map<String, Object> map) {
            String pattern = String.valueOf(map.get("pattern"));
            String path = map.containsKey("path") ? String.valueOf(map.get("path")) : ".";
            String include = map.containsKey("include") ? String.valueOf(map.get("include")) : null;
            return new Input(pattern, path, include);
        }
    }

    private record GrepMatch(String file, int line, String text) {}

    private GrepTool() {}

    public static String execute(Input input) {
        Pattern regex;
        try {
            regex = Pattern.compile(input.pattern());
        } catch (PatternSyntaxException e) {
            return "Error: invalid regex pattern: " + e.getMessage();
        }

        Path searchPath = Path.of(input.path());
        if (!Files.exists(searchPath)) {
            return "Error: path not found: " + input.path();
        }

        List<GrepMatch> matches = new ArrayList<>();

        try {
            if (Files.isRegularFile(searchPath)) {
                searchFile(searchPath, searchPath.toString(), regex, matches);
            } else if (Files.isDirectory(searchPath)) {
                Path base = searchPath.toAbsolutePath().normalize();
                Files.walkFileTree(base, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String name = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (SKIP_DIRS.contains(name)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (matches.size() >= MAX_MATCHES) {
                            return FileVisitResult.TERMINATE;
                        }
                        if (input.include() != null) {
                            String fileName = file.getFileName().toString();
                            if (!fnmatch(fileName, input.include())) {
                                return FileVisitResult.CONTINUE;
                            }
                        }
                        Path relPath = base.relativize(file);
                        searchFile(file, relPath.toString().replace('\\', '/'), regex, matches);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } else {
                return "Error: invalid path: " + input.path();
            }
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }

        if (matches.isEmpty()) {
            return "No matches for \"" + input.pattern() + "\"";
        }

        String output = matches.stream()
                .map(m -> m.file() + ":" + m.line() + ": " + m.text())
                .collect(Collectors.joining("\n"));
        if (matches.size() >= MAX_MATCHES) {
            output += "\n\n(showing first " + MAX_MATCHES + " matches)";
        }
        return output;
    }

    private static void searchFile(Path filePath, String relPath, Pattern regex, List<GrepMatch> matches) {
        try {
            long size = Files.size(filePath);
            if (size > MAX_FILE_SIZE) {
                return;
            }
            byte[] raw = Files.readAllBytes(filePath);
            int checkLen = Math.min(raw.length, 8192);
            for (int i = 0; i < checkLen; i++) {
                if (raw[i] == 0) {
                    return;
                }
            }
            String content = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (matches.size() >= MAX_MATCHES) {
                    return;
                }
                if (regex.matcher(lines[i]).find()) {
                    matches.add(new GrepMatch(relPath, i + 1, lines[i]));
                }
            }
        } catch (IOException ignored) {
            // skip unreadable files
        }
    }

    private static boolean fnmatch(String name, String pattern) {
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return name.matches(regex);
    }
}

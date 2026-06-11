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
import java.util.stream.Collectors;

public final class GlobTool {
    public static final Tool DEFINITION = new Tool(
            "glob",
            "Find files matching a glob-like pattern. "
                    + "Searches recursively from the given directory. "
                    + "Returns matching file paths sorted alphabetically.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "pattern", Map.of(
                                    "type", "string",
                                    "description", "The pattern to match files against (e.g. \"*.ts\", \"src/**/*.py\")"
                            ),
                            "path", Map.of(
                                    "type", "string",
                                    "description", "The directory to search in (default: current directory)"
                            )
                    ),
                    "required", List.of("pattern")
            )
    );

    public static final int MAX_RESULTS = 200;

    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "__pycache__", ".venv", "dist", "build",
            ".next", ".cache", "coverage", "target"
    );

    public record Input(String pattern, String path) {
        public static Input fromMap(Map<String, Object> map) {
            String pattern = String.valueOf(map.get("pattern"));
            String path = map.containsKey("path") ? String.valueOf(map.get("path")) : ".";
            return new Input(pattern, path);
        }
    }

    private GlobTool() {}

    public static String execute(Input input) {
        Path searchPath = Path.of(input.path()).toAbsolutePath().normalize();
        if (!Files.exists(searchPath)) {
            return "Error: directory not found: " + input.path();
        }
        if (!Files.isDirectory(searchPath)) {
            return "Error: not a directory: " + input.path();
        }

        List<String> results = new ArrayList<>();
        boolean hasDoubleStar = input.pattern().contains("**");
        java.nio.file.PathMatcher matcher = searchPath.getFileSystem()
                .getPathMatcher("glob:" + input.pattern());

        try {
            Files.walkFileTree(searchPath, new SimpleFileVisitor<>() {
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
                    if (results.size() >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }
                    Path relPath = searchPath.relativize(file);
                    String relStr = relPath.toString().replace('\\', '/');
                    boolean match;
                    if (hasDoubleStar) {
                        match = matcher.matches(relPath) || fnmatch(relStr, input.pattern());
                    } else {
                        String fileName = file.getFileName().toString();
                        match = fnmatch(fileName, input.pattern());
                    }
                    if (match) {
                        results.add(relStr);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }

        results.sort(String::compareTo);
        if (results.isEmpty()) {
            return "No files matching \"" + input.pattern() + "\" found in " + input.path();
        }

        String output = results.stream().collect(Collectors.joining("\n"));
        if (results.size() >= MAX_RESULTS) {
            output += "\n\n(showing first " + MAX_RESULTS + " matches)";
        }
        return output;
    }

    private static boolean fnmatch(String name, String pattern) {
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return name.matches(regex);
    }
}

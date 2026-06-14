package com.aicode.agent.tools;

import com.aicode.agent.llm.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ListDirTool {
    public static final Tool DEFINITION = new Tool(
            "list_dir",
            "List files and directories at a path. Use to explore project structure before reading files.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "path", Map.of(
                                    "type", "string",
                                    "description", "Directory to list (default: current directory)"
                            ),
                            "depth", Map.of(
                                    "type", "number",
                                    "description", "Recursion depth (1 = immediate children only, max 3, default: 1)"
                            )
                    )
            )
    );

    public static final int MAX_ENTRIES = 300;

    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "__pycache__", ".venv", "dist", "build",
            ".next", ".cache", "coverage", "target"
    );

    public record Input(String path, int depth) {
        public static Input fromMap(Map<String, Object> map) {
            String path = map.containsKey("path") ? String.valueOf(map.get("path")) : ".";
            int depth = 1;
            if (map.containsKey("depth")) {
                depth = ((Number) map.get("depth")).intValue();
            }
            return new Input(path, depth);
        }
    }

    private ListDirTool() {}

    public static String execute(Input input) {
        int depth = Math.max(1, Math.min(3, input.depth()));
        Path root = Path.of(input.path());
        if (!Files.exists(root)) {
            return "Error: path not found: " + input.path();
        }
        if (!Files.isDirectory(root)) {
            return "Error: not a directory: " + input.path();
        }

        List<String> lines = new ArrayList<>();
        lines.add(root.toString().replace('\\', '/') + "/");
        try {
            collect(root, root, depth, 0, lines);
        } catch (IOException e) {
            return "Error: cannot list directory: " + e.getMessage();
        }

        if (lines.size() > MAX_ENTRIES + 1) {
            return String.join("\n", lines.subList(0, MAX_ENTRIES + 1))
                    + "\n... (" + (lines.size() - MAX_ENTRIES - 1) + " more entries truncated)";
        }
        return String.join("\n", lines);
    }

    private static void collect(Path root, Path dir, int maxDepth, int currentDepth, List<String> lines)
            throws IOException {
        if (currentDepth >= maxDepth) {
            return;
        }

        List<Path> children;
        try (Stream<Path> stream = Files.list(dir)) {
            children = stream.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                    .toList();
        }

        for (Path child : children) {
            if (lines.size() > MAX_ENTRIES) {
                return;
            }
            String name = child.getFileName().toString();
            if (Files.isDirectory(child) && SKIP_DIRS.contains(name)) {
                continue;
            }

            String indent = "  ".repeat(currentDepth + 1);
            if (Files.isDirectory(child)) {
                lines.add(indent + name + "/");
                collect(root, child, maxDepth, currentDepth + 1, lines);
            } else {
                lines.add(indent + name);
            }
        }
    }
}

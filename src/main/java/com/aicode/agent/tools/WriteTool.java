package com.aicode.agent.tools;

import com.aicode.agent.llm.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class WriteTool {
    public static final Tool DEFINITION = new Tool(
            "write_file",
            "Write content to a file. Creates the file if it doesn't exist, "
                    + "or overwrites it if it does. Automatically creates parent directories.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "file_path", Map.of(
                                    "type", "string",
                                    "description", "The absolute or relative path to the file to write"
                            ),
                            "content", Map.of(
                                    "type", "string",
                                    "description", "The content to write to the file"
                            )
                    ),
                    "required", List.of("file_path", "content")
            )
    );

    public record Input(String filePath, String content) {
        public static Input fromMap(Map<String, Object> map) {
            return new Input(
                    String.valueOf(map.get("file_path")),
                    String.valueOf(map.get("content"))
            );
        }
    }

    private WriteTool() {}

    public static String execute(Input input) {
        Path path = Path.of(input.filePath());
        String oldContent = null;

        if (Files.isRegularFile(path)) {
            try {
                oldContent = Files.readString(path);
            } catch (IOException ignored) {
                // proceed without diff
            }
        }

        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, input.content());
        } catch (IOException e) {
            return "Error: cannot write file: " + e.getMessage();
        }

        int lines = input.content().split("\n", -1).length;
        if (oldContent == null) {
            return "Created " + input.filePath() + " (" + lines + " lines)";
        }

        String diff = generateDiff(oldContent, input.content(), input.filePath());
        return "Updated " + input.filePath() + " (" + lines + " lines)\n\n" + diff;
    }

    private static String generateDiff(String oldContent, String newContent, String filePath) {
        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);
        StringBuilder result = new StringBuilder();
        result.append("--- a/").append(filePath).append("\n");
        result.append("+++ b/").append(filePath).append("\n");

        int maxLen = Math.max(oldLines.length, newLines.length);
        boolean hasChanges = false;
        List<String> hunks = new java.util.ArrayList<>();

        for (int i = 0; i < maxLen; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : null;
            String newLine = i < newLines.length ? newLines[i] : null;
            if (java.util.Objects.equals(oldLine, newLine)) {
                hunks.add(" " + oldLine);
            } else {
                hasChanges = true;
                if (oldLine != null) {
                    hunks.add("-" + oldLine);
                }
                if (newLine != null) {
                    hunks.add("+" + newLine);
                }
            }
        }

        if (!hasChanges) {
            return "(no changes)";
        }

        result.append(String.format("@@ -1,%d +1,%d @@\n", oldLines.length, newLines.length));
        for (String hunk : hunks) {
            result.append(hunk).append("\n");
        }
        return result.toString().stripTrailing();
    }
}

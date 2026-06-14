package com.aicode.agent.tools;

import com.aicode.agent.llm.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class SearchReplaceTool {
    public static final Tool DEFINITION = new Tool(
            "search_replace",
            "Replace a unique string in a file. Prefer this over write_file for partial edits. "
                    + "Set replace_all=true to replace every occurrence.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "file_path", Map.of(
                                    "type", "string",
                                    "description", "The path to the file to edit"
                            ),
                            "old_string", Map.of(
                                    "type", "string",
                                    "description", "The exact text to replace"
                            ),
                            "new_string", Map.of(
                                    "type", "string",
                                    "description", "The replacement text"
                            ),
                            "replace_all", Map.of(
                                    "type", "boolean",
                                    "description", "Replace all occurrences (default: false, requires exactly one match)"
                            )
                    ),
                    "required", List.of("file_path", "old_string", "new_string")
            )
    );

    public record Input(String filePath, String oldString, String newString, boolean replaceAll) {
        public static Input fromMap(Map<String, Object> map) {
            boolean replaceAll = map.containsKey("replace_all")
                    && Boolean.TRUE.equals(map.get("replace_all"));
            return new Input(
                    String.valueOf(map.get("file_path")),
                    String.valueOf(map.get("old_string")),
                    String.valueOf(map.get("new_string")),
                    replaceAll
            );
        }
    }

    private SearchReplaceTool() {}

    public static String execute(Input input) {
        Path path = Path.of(input.filePath());
        if (!Files.isRegularFile(path)) {
            return "Error: file not found: " + input.filePath();
        }

        String oldContent;
        try {
            oldContent = Files.readString(path);
        } catch (IOException e) {
            return "Error: cannot read file: " + e.getMessage();
        }

        if (input.oldString().isEmpty()) {
            return "Error: old_string must not be empty";
        }

        int count = countOccurrences(oldContent, input.oldString());
        if (count == 0) {
            return "Error: old_string not found in " + input.filePath();
        }
        if (!input.replaceAll() && count > 1) {
            return "Error: old_string appears " + count + " times; use replace_all=true or provide a more specific old_string";
        }

        String newContent = input.replaceAll()
                ? oldContent.replace(input.oldString(), input.newString())
                : replaceFirst(oldContent, input.oldString(), input.newString());

        if (oldContent.equals(newContent)) {
            return "Updated " + input.filePath() + " (no changes)\n\n(no changes)";
        }

        try {
            Files.writeString(path, newContent);
        } catch (IOException e) {
            return "Error: cannot write file: " + e.getMessage();
        }

        String diff = FileDiff.generate(oldContent, newContent, input.filePath());
        int replacements = input.replaceAll() ? count : 1;
        return "Updated " + input.filePath() + " (" + replacements + " replacement(s))\n\n" + diff;
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String replaceFirst(String text, String oldString, String newString) {
        int idx = text.indexOf(oldString);
        if (idx < 0) {
            return text;
        }
        return text.substring(0, idx) + newString + text.substring(idx + oldString.length());
    }
}

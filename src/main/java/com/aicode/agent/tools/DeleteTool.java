package com.aicode.agent.tools;

import com.aicode.agent.llm.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class DeleteTool {
    public static final Tool DEFINITION = new Tool(
            "delete_file",
            "Delete a file. Prefer this over shell rm. Does not delete non-empty directories.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "file_path", Map.of(
                                    "type", "string",
                                    "description", "The path to the file to delete"
                            )
                    ),
                    "required", List.of("file_path")
            )
    );

    public record Input(String filePath) {
        public static Input fromMap(Map<String, Object> map) {
            return new Input(String.valueOf(map.get("file_path")));
        }
    }

    private DeleteTool() {}

    public static String execute(Input input) {
        Path path = Path.of(input.filePath());
        if (!Files.exists(path)) {
            return "Error: file not found: " + input.filePath();
        }
        if (Files.isDirectory(path)) {
            return "Error: path is a directory (delete_file only removes files): " + input.filePath();
        }

        try {
            Files.delete(path);
            return "Deleted " + input.filePath();
        } catch (IOException e) {
            return "Error: cannot delete file: " + e.getMessage();
        }
    }
}

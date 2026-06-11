package com.aicode.agent.tools;

import com.aicode.agent.llm.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ReadTool {
    public static final Tool DEFINITION = new Tool(
            "read_file",
            "Read the contents of a file. Returns the file content with line numbers. "
                    + "Use offset and limit to read specific portions of large files.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "file_path", Map.of(
                                    "type", "string",
                                    "description", "The absolute or relative path to the file to read"
                            ),
                            "offset", Map.of(
                                    "type", "number",
                                    "description", "Line number to start reading from (1-based, default: 1)"
                            ),
                            "limit", Map.of(
                                    "type", "number",
                                    "description", "Maximum number of lines to read (default: all)"
                            )
                    ),
                    "required", List.of("file_path")
            )
    );

    public static final int MAX_FILE_SIZE = 1024 * 1024;

    public record Input(String filePath, int offset, Integer limit) {
        public static Input fromMap(Map<String, Object> map) {
            String filePath = String.valueOf(map.get("file_path"));
            int offset = map.containsKey("offset") ? ((Number) map.get("offset")).intValue() : 1;
            Integer limit = map.containsKey("limit") ? ((Number) map.get("limit")).intValue() : null;
            return new Input(filePath, offset, limit);
        }
    }

    private ReadTool() {}

    public static String execute(Input input) {
        if (input.offset() < 1) {
            return "Error: offset must be >= 1";
        }

        Path path = Path.of(input.filePath());
        if (!Files.exists(path)) {
            return "Error: file not found: " + input.filePath();
        }
        if (!Files.isRegularFile(path)) {
            return "Error: not a file: " + input.filePath();
        }

        try {
            long fileSize = Files.size(path);
            if (fileSize > MAX_FILE_SIZE) {
                return "Error: file too large (" + fileSize + " bytes, max " + MAX_FILE_SIZE + ")";
            }

            byte[] raw = Files.readAllBytes(path);
            if (isBinary(raw)) {
                return "Error: binary file detected: " + input.filePath();
            }

            String content = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
            String[] allLines = content.split("\n", -1);
            if (allLines.length > 0 && allLines[allLines.length - 1].isEmpty()) {
                allLines = java.util.Arrays.copyOf(allLines, allLines.length - 1);
            }

            int startIdx = input.offset() - 1;
            int endIdx = input.limit() != null ? startIdx + input.limit() : allLines.length;
            if (startIdx >= allLines.length) {
                return "(empty: file has " + allLines.length + " lines, offset " + input.offset() + " is out of range)";
            }
            endIdx = Math.min(endIdx, allLines.length);

            StringBuilder selected = new StringBuilder();
            for (int i = startIdx; i < endIdx; i++) {
                if (i > startIdx) {
                    selected.append("\n");
                }
                selected.append(allLines[i]);
            }
            if (selected.isEmpty()) {
                return "(empty: file has " + allLines.length + " lines, offset " + input.offset() + " is out of range)";
            }

            return formatWithLineNumbers(selected.toString(), input.offset());
        } catch (IOException e) {
            return "Error: cannot read file: " + e.getMessage();
        }
    }

    private static boolean isBinary(byte[] data) {
        int checkLength = Math.min(data.length, 8192);
        for (int i = 0; i < checkLength; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }

    private static String formatWithLineNumbers(String content, int offset) {
        String[] lines = content.split("\n", -1);
        int maxLineNum = offset + lines.length - 1;
        int padWidth = String.valueOf(maxLineNum).length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(String.format("%" + padWidth + "d", offset + i)).append("\t").append(lines[i]);
        }
        return sb.toString();
    }
}

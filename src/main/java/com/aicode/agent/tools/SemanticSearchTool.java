package com.aicode.agent.tools;

import com.aicode.agent.llm.Tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class SemanticSearchTool {
    public static final Tool DEFINITION = new Tool(
            "semantic_search",
            "Search the codebase by meaning/keywords. Use for concepts (e.g. \"payment failure handling\") "
                    + "when grep exact symbols is insufficient. Returns ranked code chunks with file paths.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "query", Map.of(
                                    "type", "string",
                                    "description", "Natural language or keyword description of what to find"
                            ),
                            "path", Map.of(
                                    "type", "string",
                                    "description", "Optional subdirectory to limit search (default: workspace root)"
                            ),
                            "limit", Map.of(
                                    "type", "number",
                                    "description", "Max results (default: 8, max: 25)"
                            )
                    ),
                    "required", List.of("query")
            )
    );

    public record Input(String query, String path, int limit) {
        public static Input fromMap(Map<String, Object> map) {
            String query = String.valueOf(map.get("query"));
            String path = map.containsKey("path") ? String.valueOf(map.get("path")) : null;
            int limit = 8;
            if (map.containsKey("limit")) {
                limit = ((Number) map.get("limit")).intValue();
            }
            return new Input(query, path, limit);
        }
    }

    private SemanticSearchTool() {}

    public static String execute(Input input, Path workspace) {
        return execute(input, workspace, null);
    }

    public static String execute(Input input, Path workspace, com.aicode.agent.llm.EmbeddingProvider embeddingProvider) {
        if (input.query() == null || input.query().isBlank()) {
            return "Error: query is required";
        }
        if (!Files.isDirectory(workspace)) {
            return "Error: workspace not found";
        }
        int limit = Math.max(1, Math.min(25, input.limit()));
        CodebaseSearch.Result result = CodebaseSearch.search(
                workspace, input.query(), limit, input.path(), embeddingProvider);
        return CodebaseSearch.formatResults(result);
    }
}

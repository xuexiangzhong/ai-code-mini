package com.aicode.agent.llm;

import java.util.Map;

public record Tool(String name, String description, Map<String, Object> inputSchema) {
    public Tool {
        if (inputSchema == null) {
            inputSchema = Map.of();
        }
    }
}

package com.aicode.agent.llm;

import java.util.List;
import java.util.function.BooleanSupplier;

public record ChatOptions(String system, Integer maxTokens, List<Tool> tools, BooleanSupplier cancelCheck) {
    public ChatOptions() {
        this(null, null, null, null);
    }

    public ChatOptions(String system, Integer maxTokens, List<Tool> tools) {
        this(system, maxTokens, tools, null);
    }

    public static boolean shouldCancel(ChatOptions options) {
        return options != null
                && options.cancelCheck() != null
                && options.cancelCheck().getAsBoolean();
    }

    public ChatOptions withSystem(String system) {
        return new ChatOptions(system, maxTokens, tools, cancelCheck);
    }

    public ChatOptions withMaxTokens(Integer maxTokens) {
        return new ChatOptions(system, maxTokens, tools, cancelCheck);
    }

    public ChatOptions withTools(List<Tool> tools) {
        return new ChatOptions(system, maxTokens, tools, cancelCheck);
    }

    public ChatOptions withCancelCheck(BooleanSupplier cancelCheck) {
        return new ChatOptions(system, maxTokens, tools, cancelCheck);
    }
}

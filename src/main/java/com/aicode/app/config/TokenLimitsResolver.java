package com.aicode.app.config;

import com.aicode.agent.llm.OutputTokenLimits;

/** Resolve context/output token limits from {@link ModelProfile}, with model-name and app defaults. */
public final class TokenLimitsResolver {
    private TokenLimitsResolver() {}

    public static AppConfig applyProfileTokenLimits(AppConfig config, ModelProfile profile) {
        int contextWindow = effectiveContextWindow(profile.model(), profile.contextWindow());
        int maxOutput = positiveOrDefault(profile.maxOutputTokens(), OutputTokenLimits.DEFAULT_BASE);
        int maxCap = positiveOrDefault(profile.maxOutputTokenCap(), OutputTokenLimits.DEFAULT_CAP);
        int maxRetries = positiveOrDefault(profile.maxOutputRetries(), OutputTokenLimits.DEFAULT_RETRIES);
        AppConfig withTokens = config.withTokenLimits(contextWindow, maxOutput, maxCap, maxRetries);
        int maxIterations = positiveOrDefault(profile.maxIterations(), AppConfig.withDefaults().maxIterations());
        boolean parallelTools = profile.parallelToolCalls() != null
                ? profile.parallelToolCalls()
                : AppConfig.withDefaults().parallelToolCalls();
        String embeddingModel = profile.embeddingModel() != null ? profile.embeddingModel() : "";
        return withTokens.withAgentSettings(maxIterations, parallelTools, embeddingModel);
    }

    public static int effectiveContextWindow(String model, Integer userValue) {
        Integer configured = positiveOrNull(userValue);
        if (configured != null) {
            return configured;
        }
        return ModelContextLimits.forModel(model);
    }

    private static int positiveOrDefault(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    private static Integer positiveOrNull(Integer value) {
        return value != null && value > 0 ? value : null;
    }
}

package com.aicode.app.config;

import java.util.Locale;
import java.util.Map;

/** Default context-window sizes inferred from model id when the user does not set one. */
public final class ModelContextLimits {
    private static final int DEFAULT = AppConfig.defaultContextWindow();

    private static final Map<String, Integer> EXACT = Map.ofEntries(
            Map.entry("deepseek-chat", 64_000),
            Map.entry("deepseek-coder", 128_000),
            Map.entry("gpt-4o", 128_000),
            Map.entry("gpt-4o-mini", 128_000),
            Map.entry("claude-sonnet-4-20250514", 200_000),
            Map.entry("claude-haiku-4-20250414", 200_000),
            Map.entry("claude-3-5-sonnet-20241022", 200_000)
    );

    private ModelContextLimits() {}

    public static int forModel(String model) {
        if (model == null || model.isBlank()) {
            return DEFAULT;
        }
        String normalized = model.strip().toLowerCase(Locale.ROOT);
        Integer exact = EXACT.get(normalized);
        if (exact != null) {
            return exact;
        }
        if (normalized.contains("claude")) {
            return 200_000;
        }
        if (normalized.contains("deepseek")) {
            return normalized.contains("coder") ? 128_000 : 64_000;
        }
        if (normalized.contains("gpt-4o")) {
            return 128_000;
        }
        if (normalized.contains("qwen")) {
            if (normalized.contains("max")
                    || normalized.contains("plus")
                    || normalized.contains("235b")
                    || normalized.contains("72b")
                    || normalized.contains("3.7")) {
                return 131_072;
            }
            if (normalized.contains("turbo") || normalized.contains("long")) {
                return 131_072;
            }
            return 32_768;
        }
        if (normalized.contains("llama") || normalized.contains("mistral") || normalized.contains("gemma")) {
            return 32_768;
        }
        return DEFAULT;
    }
}

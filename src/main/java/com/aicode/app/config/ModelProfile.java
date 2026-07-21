package com.aicode.app.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.aicode.agent.llm.OutputTokenLimits;

import java.nio.file.Path;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelProfile(
        String id,
        String name,
        String baseUrl,
        String apiKey,
        String model,
        String providerType,
        Integer contextWindow,
        Integer maxOutputTokens,
        Integer maxOutputTokenCap,
        Integer maxOutputRetries,
        Integer maxIterations,
        Boolean parallelToolCalls,
        String embeddingModel
) {
    public static ModelProfile createDefault() {
        String id = java.util.UUID.randomUUID().toString().substring(0, 8);
        AppConfig defaults = AppConfig.withDefaults();
        return new ModelProfile(
                id,
                "新模型",
                defaults.baseUrl(),
                "",
                defaults.model(),
                defaults.providerType(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    @JsonIgnore
    public boolean isUsable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @JsonIgnore
    public String displayLabel() {
        return name + " · " + model;
    }

    @JsonIgnore
    public int effectiveContextWindow() {
        return TokenLimitsResolver.effectiveContextWindow(model, contextWindow);
    }

    @JsonIgnore
    public String contextWindowLabel() {
        if (contextWindow != null && contextWindow > 0) {
            return formatTokens(contextWindow) + " (自定义)";
        }
        return formatTokens(ModelContextLimits.forModel(model)) + " (自动)";
    }

    @JsonIgnore
    public AppConfig toAppConfig(Path workspace) {
        return AppConfigLoader.forModelProfile(this, workspace);
    }

    public ModelProfile withValues(String name, String baseUrl, String apiKey, String model, String providerType) {
        return withValues(name, baseUrl, apiKey, model, providerType, contextWindow);
    }

    public ModelProfile withValues(
            String name,
            String baseUrl,
            String apiKey,
            String model,
            String providerType,
            Integer contextWindow
    ) {
        return withExtendedValues(
                name, baseUrl, apiKey, model, providerType, contextWindow,
                maxIterations, parallelToolCalls, embeddingModel
        );
    }

    public ModelProfile withExtendedValues(
            String name,
            String baseUrl,
            String apiKey,
            String model,
            String providerType,
            Integer contextWindow,
            Integer maxIterations,
            Boolean parallelToolCalls,
            String embeddingModel
    ) {
        return new ModelProfile(
                id,
                name != null && !name.isBlank() ? name : this.name,
                baseUrl != null && !baseUrl.isBlank() ? baseUrl : this.baseUrl,
                apiKey != null ? apiKey : this.apiKey,
                model != null && !model.isBlank() ? model : this.model,
                providerType != null && !providerType.isBlank() ? providerType : this.providerType,
                contextWindow != null ? contextWindow : this.contextWindow,
                maxOutputTokens,
                maxOutputTokenCap,
                maxOutputRetries,
                maxIterations != null ? maxIterations : this.maxIterations,
                parallelToolCalls != null ? parallelToolCalls : this.parallelToolCalls,
                embeddingModel != null ? embeddingModel : this.embeddingModel
        );
    }

    public static ModelProfile of(
            String id,
            String name,
            String baseUrl,
            String apiKey,
            String model,
            String providerType
    ) {
        return new ModelProfile(
                id, name, baseUrl, apiKey, model, providerType,
                null, null, null, null, null, null, null
        );
    }

    private static String formatTokens(int value) {
        if (value >= 1000 && value % 1000 == 0) {
            return (value / 1000) + "k";
        }
        return String.valueOf(value);
    }
}

package com.aicode.app.config;

import java.nio.file.Path;

/**
 * Unified configuration: {@code ~/.aicode/models.json} for model/API/token limits,
 * {@code ./aicode.yaml} for workspace and agent display settings only.
 */
public final class AppConfigLoader {
    private AppConfigLoader() {}

    public static AppConfig load() {
        AppConfig yaml = AppConfig.applyEnvironment(AppConfigStore.loadYaml());
        ModelRegistry registry = ModelRegistry.load();
        if (registry.hasUsableModel()) {
            return forModelProfile(registry.defaultModel(), yaml.workspace());
        }
        return yaml;
    }

    public static AppConfig forModelProfile(ModelProfile profile, Path workspace) {
        AppConfig yaml = AppConfig.applyEnvironment(AppConfigStore.loadYaml());
        AppConfig base = AppConfig.withDefaults().withValues(
                profile.apiKey(),
                profile.baseUrl(),
                profile.model(),
                profile.providerType(),
                yaml.agentName(),
                yaml.agentIcon(),
                workspace != null ? workspace : yaml.workspace()
        );
        return TokenLimitsResolver.applyProfileTokenLimits(base, profile);
    }
}

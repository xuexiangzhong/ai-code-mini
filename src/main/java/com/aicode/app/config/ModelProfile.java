package com.aicode.app.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.file.Path;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelProfile(
        String id,
        String name,
        String baseUrl,
        String apiKey,
        String model,
        String providerType
) {
    public static ModelProfile createDefault() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        AppConfig defaults = AppConfig.withDefaults();
        return new ModelProfile(
                id,
                "新模型",
                defaults.baseUrl(),
                "",
                defaults.model(),
                defaults.providerType()
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
    public AppConfig toAppConfig(Path workspace) {
        AppConfig base = AppConfig.withDefaults();
        return base.withValues(
                apiKey,
                baseUrl,
                model,
                providerType,
                base.agentName(),
                base.agentIcon(),
                workspace
        );
    }

    public ModelProfile withValues(String name, String baseUrl, String apiKey, String model, String providerType) {
        return new ModelProfile(
                id,
                name != null && !name.isBlank() ? name : this.name,
                baseUrl != null && !baseUrl.isBlank() ? baseUrl : this.baseUrl,
                apiKey != null ? apiKey : this.apiKey,
                model != null && !model.isBlank() ? model : this.model,
                providerType != null && !providerType.isBlank() ? providerType : this.providerType
        );
    }
}

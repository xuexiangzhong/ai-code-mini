package com.aicode.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ModelRegistry {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final List<ModelProfile> models = new ArrayList<>();
    private String defaultModelId = "";

    public static ModelRegistry load() {
        ModelRegistry registry = new ModelRegistry();
        Path file = AicodePaths.modelsFile();
        if (Files.isRegularFile(file)) {
            try {
                registry.applyData(MAPPER.readValue(file.toFile(), ModelRegistryData.class));
            } catch (IOException e) {
                System.err.println("Warning: failed to read " + file + ": " + e.getMessage());
            }
        } else {
            registry.migrateLegacyConfig();
        }
        registry.applyEnvironmentDefaults();
        return registry;
    }

    public void save() throws IOException {
        AicodePaths.ensureRootExists();
        ModelRegistryData data = new ModelRegistryData();
        data.defaultModelId = defaultModelId;
        data.models = List.copyOf(models);
        MAPPER.writeValue(AicodePaths.modelsFile().toFile(), data);
    }

    public List<ModelProfile> models() {
        return List.copyOf(models);
    }

    public boolean hasUsableModel() {
        return models.stream().anyMatch(ModelProfile::isUsable);
    }

    public Optional<ModelProfile> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return models.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    public ModelProfile defaultModel() {
        return findById(defaultModelId)
                .or(() -> models.stream().filter(ModelProfile::isUsable).findFirst())
                .or(() -> models.stream().findFirst())
                .orElse(ModelProfile.createDefault());
    }

    public String defaultModelId() {
        return defaultModelId;
    }

    public void setDefaultModelId(String id) {
        this.defaultModelId = id != null ? id : "";
    }

    public void add(ModelProfile profile) {
        models.add(profile);
        if (defaultModelId.isBlank()) {
            defaultModelId = profile.id();
        }
    }

    public void update(ModelProfile profile) {
        for (int i = 0; i < models.size(); i++) {
            if (models.get(i).id().equals(profile.id())) {
                models.set(i, profile);
                return;
            }
        }
        add(profile);
    }

    public void remove(String id) {
        models.removeIf(m -> m.id().equals(id));
        if (defaultModelId.equals(id)) {
            defaultModelId = models.isEmpty() ? "" : models.getFirst().id();
        }
    }

    private void applyData(ModelRegistryData data) {
        models.clear();
        if (data.models != null) {
            models.addAll(data.models);
        }
        defaultModelId = data.defaultModelId != null ? data.defaultModelId : "";
    }

    private void migrateLegacyConfig() {
        AppConfig legacy = AppConfigStore.load();
        if (!legacy.isConfigured()) {
            return;
        }
        String id = "migrated";
        add(new ModelProfile(
                id,
                "已迁移配置",
                legacy.baseUrl(),
                legacy.apiKey(),
                legacy.model(),
                legacy.providerType()
        ));
        defaultModelId = id;
        try {
            save();
        } catch (IOException e) {
            System.err.println("Warning: migrated models not saved: " + e.getMessage());
        }
    }

    private void applyEnvironmentDefaults() {
        if (!models.isEmpty()) {
            return;
        }
        AppConfig env = AppConfig.applyEnvironment(AppConfig.withDefaults());
        if (!env.isConfigured()) {
            return;
        }
        String id = "env";
        add(new ModelProfile(
                id,
                "环境变量",
                env.baseUrl(),
                env.apiKey(),
                env.model(),
                env.providerType()
        ));
        defaultModelId = id;
    }

    public static ModelProfile newProfile() {
        return ModelProfile.createDefault();
    }
}

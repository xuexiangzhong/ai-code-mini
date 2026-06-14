package com.aicode.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ModelRegistry {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final List<ModelProfile> models = new ArrayList<>();
    private String defaultModelId = "";

    public static ModelRegistry load() {
        ModelRegistry registry = new ModelRegistry();
        Path userFile = AicodePaths.modelsFile();
        if (Files.isRegularFile(userFile)) {
            try {
                registry.applyData(MAPPER.readValue(userFile.toFile(), ModelRegistryData.class));
            } catch (IOException e) {
                System.err.println("Warning: failed to read " + userFile + ": " + e.getMessage());
            }
        } else {
            registry.migrateLegacyYaml();
        }
        registry.applyEnvironmentDefaults();
        return registry;
    }

    public void save() throws IOException {
        AicodePaths.ensureUserRootExists();
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

    private void migrateLegacyYaml() {
        AppConfigStore.LegacyYamlLoad legacyLoad = AppConfigStore.loadLegacyYamlForMigration();
        AppConfig legacy = AppConfig.applyEnvironment(legacyLoad.config());
        if (!legacyLoad.hasApiKey() || !legacy.isConfigured()) {
            return;
        }
        String id = "migrated";
        Integer contextWindow = legacyLoad.hasContextWindow() && legacy.contextWindow() > 0
                ? legacy.contextWindow()
                : null;
        Integer maxOutput = legacyLoad.hasMaxOutputTokens() && legacy.maxOutputTokens() > 0
                ? legacy.maxOutputTokens()
                : null;
        Integer maxCap = legacyLoad.hasMaxOutputTokenCap() && legacy.maxOutputTokenCap() > 0
                ? legacy.maxOutputTokenCap()
                : null;
        add(new ModelProfile(
                id,
                "已迁移配置",
                legacy.baseUrl(),
                legacy.apiKey(),
                legacy.model(),
                legacy.providerType(),
                contextWindow,
                maxOutput,
                maxCap,
                legacy.maxOutputRetries() > 0 ? legacy.maxOutputRetries() : null
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
        String apiKey = firstNonBlank(
                env("DEEPSEEK_API_KEY", ""),
                env("OPENAI_API_KEY", "")
        );
        if (apiKey.isBlank()) {
            return;
        }
        AppConfig env = AppConfig.withDefaults();
        String id = "env";
        add(ModelProfile.of(
                id,
                "环境变量",
                envOrDefault("LLM_BASE_URL", env.baseUrl()),
                apiKey,
                envOrDefault("LLM_MODEL", env.model()),
                envOrDefault("LLM_PROVIDER", env.providerType())
        ));
        defaultModelId = id;
    }

    public static ModelProfile newProfile() {
        return ModelProfile.createDefault();
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}

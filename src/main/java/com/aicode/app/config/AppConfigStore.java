package com.aicode.app.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads and saves {@link AppConfig} to {@code ./aicode.yaml} in the current working directory.
 */
public final class AppConfigStore {
    public static final String FILE_NAME = "aicode.yaml";
    private static final String LEGACY_FILE_NAME = "aicodeing.yaml";

    private AppConfigStore() {}

    public static Path configPath() {
        return WorkingDirectory.effective().resolve(FILE_NAME);
    }

    public static boolean exists() {
        return Files.isRegularFile(resolveReadableConfigPath());
    }

    public static AppConfig load() {
        AppConfig config = AppConfig.withDefaults();
        Path readable = resolveReadableConfigPath();
        if (Files.isRegularFile(readable)) {
            try {
                config = applyFile(config, readable);
            } catch (IOException e) {
                System.err.println("Warning: failed to read " + readable + ": " + e.getMessage());
            }
        }
        return AppConfig.applyEnvironment(config);
    }

    private static Path resolveReadableConfigPath() {
        Path primary = configPath();
        if (Files.isRegularFile(primary)) {
            return primary;
        }
        Path legacy = WorkingDirectory.effective().resolve(LEGACY_FILE_NAME);
        if (Files.isRegularFile(legacy)) {
            return legacy;
        }
        return primary;
    }

    public static void save(AppConfig config) throws IOException {
        Path path = configPath();
        Files.createDirectories(path.getParent());
        Files.writeString(path, toYaml(config), StandardCharsets.UTF_8);
    }

    static AppConfig applyFile(AppConfig base, Path path) throws IOException {
        Map<String, String> values = parseSimpleYaml(path);
        int legacyMaxTokens = values.containsKey("maxTokens")
                ? AppConfig.parseIntValue(values.get("maxTokens"), base.contextWindow())
                : base.contextWindow();
        int contextWindow = values.containsKey("contextWindow")
                ? AppConfig.parseIntValue(values.get("contextWindow"), base.contextWindow())
                : legacyMaxTokens;
        int maxOutputTokens = values.containsKey("maxOutputTokens")
                ? AppConfig.parseIntValue(values.get("maxOutputTokens"), base.maxOutputTokens())
                : (values.containsKey("maxTokens")
                        ? AppConfig.parseIntValue(values.get("maxTokens"), base.maxOutputTokens())
                        : base.maxOutputTokens());
        int maxOutputTokenCap = values.containsKey("maxOutputTokenCap")
                ? AppConfig.parseIntValue(values.get("maxOutputTokenCap"), base.maxOutputTokenCap())
                : base.maxOutputTokenCap();
        int maxOutputRetries = values.containsKey("maxOutputRetries")
                ? AppConfig.parseIntValue(values.get("maxOutputRetries"), base.maxOutputRetries())
                : base.maxOutputRetries();

        return base.withValues(
                values.getOrDefault("apiKey", base.apiKey()),
                values.getOrDefault("baseUrl", base.baseUrl()),
                values.getOrDefault("model", base.model()),
                values.getOrDefault("providerType", base.providerType()),
                values.getOrDefault("agentName", base.agentName()),
                values.getOrDefault("agentIcon", base.agentIcon()),
                values.containsKey("workspace") && !values.get("workspace").isBlank()
                        ? Path.of(values.get("workspace"))
                        : base.workspace()
        ).withTokenLimits(contextWindow, maxOutputTokens, maxOutputTokenCap, maxOutputRetries);
    }

    private static String toYaml(AppConfig config) {
        return """
                # AiCode local configuration (generated)
                apiKey: %s
                baseUrl: %s
                model: %s
                providerType: %s
                agentName: %s
                agentIcon: "%s"
                workspace: %s
                contextWindow: %d
                maxOutputTokens: %d
                maxOutputTokenCap: %d
                maxOutputRetries: %d
                """.formatted(
                yamlScalar(config.apiKey()),
                yamlScalar(config.baseUrl()),
                yamlScalar(config.model()),
                yamlScalar(config.providerType()),
                yamlScalar(config.agentName()),
                config.agentIcon(),
                yamlScalar(config.workspace().toString()),
                config.contextWindow(),
                config.maxOutputTokens(),
                config.maxOutputTokenCap(),
                config.maxOutputRetries()
        );
    }

    private static String yamlScalar(String value) {
        if (value == null || value.isBlank()) {
            return "\"\"";
        }
        if (value.contains(":") || value.contains("#") || value.contains("\"")) {
            return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return value;
    }

    private static Map<String, String> parseSimpleYaml(Path path) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = rawLine.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).strip();
            String value = line.substring(idx + 1).strip();
            map.put(key, unquote(value));
        }
        return map;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return value;
    }
}

package com.aicode.app.config;

import com.aicode.agent.llm.OutputTokenLimits;

import java.nio.file.Path;

/**
 * Runtime configuration assembled from {@code ~/.aicode/models.json} (model/API/tokens)
 * and {@code ./aicode.yaml} (workspace/agent display).
 */
public record AppConfig(
        String apiKey,
        String baseUrl,
        String model,
        String providerType,
        String agentName,
        String agentIcon,
        Path workspace,
        int port,
        int maxIterations,
        int contextWindow,
        int maxOutputTokens,
        int maxOutputTokenCap,
        int maxOutputRetries,
        boolean parallelToolCalls
) {
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final String DEFAULT_PROVIDER = "openai-compatible";
    private static final String DEFAULT_AGENT_NAME = "AI Coding";
    private static final String DEFAULT_AGENT_ICON = "🤖";
    private static final int DEFAULT_PORT = 8765;
    private static final int DEFAULT_MAX_ITERATIONS = 50;
    private static final int DEFAULT_CONTEXT_WINDOW = 32768;

    public static int defaultContextWindow() {
        return DEFAULT_CONTEXT_WINDOW;
    }
    private static final int DEFAULT_MAX_OUTPUT_TOKENS = OutputTokenLimits.DEFAULT_BASE;
    private static final int DEFAULT_MAX_OUTPUT_TOKEN_CAP = OutputTokenLimits.DEFAULT_CAP;
    private static final int DEFAULT_MAX_OUTPUT_RETRIES = OutputTokenLimits.DEFAULT_RETRIES;

    public static AppConfig withDefaults() {
        return new AppConfig(
                "",
                DEFAULT_BASE_URL,
                DEFAULT_MODEL,
                DEFAULT_PROVIDER,
                DEFAULT_AGENT_NAME,
                DEFAULT_AGENT_ICON,
                WorkingDirectory.defaultWorkspace(),
                DEFAULT_PORT,
                DEFAULT_MAX_ITERATIONS,
                DEFAULT_CONTEXT_WINDOW,
                DEFAULT_MAX_OUTPUT_TOKENS,
                DEFAULT_MAX_OUTPUT_TOKEN_CAP,
                DEFAULT_MAX_OUTPUT_RETRIES,
                true
        );
    }

    /** @deprecated use {@link AppConfigStore#load()} */
    @Deprecated
    public static AppConfig fromEnvironment() {
        return applyEnvironment(withDefaults());
    }

    public static AppConfig applyEnvironment(AppConfig base) {
        String workspaceEnv = env("AICODE_WORKSPACE", "");
        Path workspace = workspaceEnv.isBlank() ? base.workspace() : Path.of(workspaceEnv);

        return base.withValues(
                base.apiKey(),
                base.baseUrl(),
                base.model(),
                base.providerType(),
                envOrDefault("AGENT_NAME", base.agentName()),
                envOrDefault("AGENT_ICON", base.agentIcon()),
                workspace
        );
    }

    public AppConfig withValues(
            String apiKey,
            String baseUrl,
            String model,
            String providerType,
            String agentName,
            String agentIcon,
            Path workspace
    ) {
        return new AppConfig(
                apiKey != null ? apiKey : "",
                baseUrl != null && !baseUrl.isBlank() ? baseUrl : DEFAULT_BASE_URL,
                model != null && !model.isBlank() ? model : DEFAULT_MODEL,
                providerType != null && !providerType.isBlank() ? providerType : DEFAULT_PROVIDER,
                agentName != null && !agentName.isBlank() ? agentName : DEFAULT_AGENT_NAME,
                agentIcon != null && !agentIcon.isBlank() ? agentIcon : DEFAULT_AGENT_ICON,
                workspace != null ? WorkingDirectory.normalizeWorkspace(workspace) : WorkingDirectory.defaultWorkspace(),
                port,
                maxIterations,
                contextWindow,
                maxOutputTokens,
                maxOutputTokenCap,
                maxOutputRetries,
                parallelToolCalls
        );
    }

    public AppConfig withTokenLimits(
            int contextWindow,
            int maxOutputTokens,
            int maxOutputTokenCap,
            int maxOutputRetries
    ) {
        return new AppConfig(
                apiKey, baseUrl, model, providerType, agentName, agentIcon, workspace,
                port, maxIterations, contextWindow, maxOutputTokens, maxOutputTokenCap, maxOutputRetries,
                parallelToolCalls
        );
    }

    public OutputTokenLimits outputTokenLimits() {
        return new OutputTokenLimits(maxOutputTokens, maxOutputTokenCap, maxOutputRetries);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public void requireConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException(
                    "API Key 未配置。请在主界面「模型配置」中添加模型并填写 Key，或设置环境变量。"
            );
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.isBlank() ? value : defaultValue;
    }

    static int parseIntValue(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

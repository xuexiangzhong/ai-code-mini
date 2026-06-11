package com.aicode.agent.llm;

public final class ProviderFactory {
    public record ProviderConfig(
            String provider,
            String apiKey,
            String model,
            String baseUrl
    ) {}

    private ProviderFactory() {}

    public static LLMProvider createProvider(ProviderConfig config) {
        return switch (config.provider()) {
            case "anthropic" -> new AnthropicProvider(new AnthropicProvider.Config(
                    config.apiKey(),
                    config.model() != null ? config.model() : "claude-sonnet-4-20250514"
            ));
            case "openai-compatible" -> {
                if (config.baseUrl() == null || config.baseUrl().isBlank()) {
                    throw new IllegalArgumentException("base_url is required for openai-compatible provider");
                }
                if (config.model() == null || config.model().isBlank()) {
                    throw new IllegalArgumentException("model is required for openai-compatible provider");
                }
                yield new OpenAICompatibleProvider(new OpenAICompatibleProvider.Config(
                        config.apiKey(), config.baseUrl(), config.model()
                ));
            }
            default -> throw new IllegalArgumentException("Unknown provider: " + config.provider());
        };
    }
}

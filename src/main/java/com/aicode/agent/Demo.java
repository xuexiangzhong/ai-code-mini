package com.aicode.agent;

import com.aicode.agent.llm.LLM;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.ProviderFactory;

/**
 * Minimal LLM demo (mirrors {@code python/demo.py} and {@code typescript/demo.ts}).
 */
public final class Demo {
    private Demo() {}

    public static void main(String[] args) {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Error: Set DEEPSEEK_API_KEY or OPENAI_API_KEY environment variable.");
            System.exit(1);
        }

        var provider = LLM.createProvider(new ProviderFactory.ProviderConfig(
                "openai-compatible",
                apiKey,
                "deepseek-chat",
                "https://api.deepseek.com/v1/chat/completions"
        ));

        var response = provider.chat(
                java.util.List.of(Message.user("用一句话解释什么是 TypeScript")),
                new com.aicode.agent.llm.ChatOptions(null, 200, null)
        ).join();

        System.out.println("Response: " + response.text());
        System.out.println("Stop reason: " + response.stopReason());
        System.out.println("Usage: " + response.usage());
    }
}

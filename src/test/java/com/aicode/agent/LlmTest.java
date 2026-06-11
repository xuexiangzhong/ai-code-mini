package com.aicode.agent;

import com.aicode.agent.llm.AnthropicProvider;
import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.OpenAICompatibleProvider;
import com.aicode.agent.llm.ProviderFactory;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LlmTest {
    @Nested
    class TestCreateProvider {
        @Test
        void createAnthropicProvider() {
            LLMProvider provider = ProviderFactory.createProvider(
                    new ProviderFactory.ProviderConfig("anthropic", "key", null, null));
            assertInstanceOf(AnthropicProvider.class, provider);
        }

        @Test
        void createOpenAICompatibleProvider() {
            LLMProvider provider = ProviderFactory.createProvider(
                    new ProviderFactory.ProviderConfig(
                            "openai-compatible", "key", "model-1", "https://api.example.com"));
            assertInstanceOf(OpenAICompatibleProvider.class, provider);
        }

        @Test
        void throwIfBaseUrlMissing() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    ProviderFactory.createProvider(
                            new ProviderFactory.ProviderConfig("openai-compatible", "key", "m", null)));
            assertTrue(ex.getMessage().contains("base_url"));
        }

        @Test
        void throwIfModelMissing() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    ProviderFactory.createProvider(
                            new ProviderFactory.ProviderConfig(
                                    "openai-compatible", "key", null, "https://api.example.com")));
            assertTrue(ex.getMessage().contains("model"));
        }

        @Test
        void throwForUnknownProvider() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                    ProviderFactory.createProvider(
                            new ProviderFactory.ProviderConfig("unknown", "key", null, null)));
            assertTrue(ex.getMessage().contains("Unknown"));
        }

        @Test
        void resolveChatCompletionsUrlForDashScope() {
            assertEquals(
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                    OpenAICompatibleProvider.resolveChatCompletionsUrl(
                            "https://dashscope.aliyuncs.com/compatible-mode"));
            assertEquals(
                    "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
                    OpenAICompatibleProvider.resolveChatCompletionsUrl(
                            "https://dashscope.aliyuncs.com/compatible-mode/"));
        }

        @Test
        void resolveChatCompletionsUrlUsesConfiguredUrlAsIs() {
            assertEquals(
                    "https://api.deepseek.com/v1/chat/completions",
                    OpenAICompatibleProvider.resolveChatCompletionsUrl(
                            "https://api.deepseek.com/v1/chat/completions"));
            assertEquals(
                    "https://custom.example.com/my-endpoint",
                    OpenAICompatibleProvider.resolveChatCompletionsUrl(
                            "https://custom.example.com/my-endpoint/"));
        }
    }

    @Nested
    class TestOpenAICompatibleProvider {
        MockWebServer server;
        OkHttpClient client;

        @BeforeEach
        void setUp() throws IOException {
            server = new MockWebServer();
            server.start();
            client = new OkHttpClient.Builder().build();
        }

        @AfterEach
        void tearDown() throws IOException {
            server.shutdown();
        }

        String baseUrl() {
            return server.url("").toString().replaceAll("/$", "");
        }

        @Test
        void sendMessageAndReturnResponse() {
            server.enqueue(new MockResponse()
                    .setBody("""
                            {
                              "choices": [{
                                "message": { "content": "Hello from DeepSeek!" },
                                "finish_reason": "stop"
                              }],
                              "usage": { "prompt_tokens": 10, "completion_tokens": 5 }
                            }
                            """)
                    .addHeader("Content-Type", "application/json"));

            OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                    new OpenAICompatibleProvider.Config("test-key", baseUrl(), "deepseek-chat"), client);

            var response = provider.chat(List.of(Message.user("Hi")), new ChatOptions()).join();
            assertEquals("Hello from DeepSeek!", response.text());
            assertEquals("end_turn", response.stopReason());
            assertEquals(10, response.usage().get("input_tokens"));
            assertEquals(5, response.usage().get("output_tokens"));
        }

        @Test
        void prependSystemMessage() throws InterruptedException {
            server.enqueue(new MockResponse()
                    .setBody("""
                            {
                              "choices": [{
                                "message": { "content": "Hi" },
                                "finish_reason": "stop"
                              }],
                              "usage": { "prompt_tokens": 5, "completion_tokens": 1 }
                            }
                            """)
                    .addHeader("Content-Type", "application/json"));

            OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                    new OpenAICompatibleProvider.Config("test-key", baseUrl(), "deepseek-chat"), client);

            provider.chat(
                    List.of(Message.user("Hi")),
                    new ChatOptions("Be helpful.", null, null)
            ).join();

            var request = server.takeRequest();
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("\"role\":\"system\""));
            assertTrue(body.contains("Be helpful."));
        }
    }
}

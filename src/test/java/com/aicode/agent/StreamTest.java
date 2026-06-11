package com.aicode.agent;

import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.OpenAICompatibleProvider;
import com.aicode.agent.llm.StreamEvent;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StreamTest {
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

    OpenAICompatibleProvider provider() {
        return new OpenAICompatibleProvider(
                new OpenAICompatibleProvider.Config("test-key", baseUrl(), "deepseek-chat"), client);
    }

    List<StreamEvent> collectEvents(OpenAICompatibleProvider provider) {
        List<StreamEvent> events = new ArrayList<>();
        provider.stream(List.of(Message.user("Hi")), null, events::add);
        return events;
    }

    @Nested
    class TestOpenAICompatibleStreaming {
        @Test
        void yieldStreamEvents() {
            String sse = """
                    data: {"choices":[{"delta":{"content":"Hello"}}]}

                    data: {"choices":[{"delta":{"content":" world"}}]}

                    data: [DONE]

                    """;
            server.enqueue(new MockResponse()
                    .setBody(sse)
                    .addHeader("Content-Type", "text/event-stream"));

            List<StreamEvent> events = collectEvents(provider());

            assertEquals("message_start", events.get(0).type());
            assertEquals(new StreamEvent("text_delta", "Hello"), events.get(1));
            assertEquals(new StreamEvent("text_delta", " world"), events.get(2));
            assertEquals("message_stop", events.get(3).type());
            assertNotNull(events.get(3).response());
            assertEquals("Hello world", events.get(3).response().text());
        }

        @Test
        void fullTextFromDeltas() {
            String sse = """
                    data: {"choices":[{"delta":{"content":"Hello"}}]}

                    data: {"choices":[{"delta":{"content":" world"}}]}

                    data: [DONE]

                    """;
            server.enqueue(new MockResponse()
                    .setBody(sse)
                    .addHeader("Content-Type", "text/event-stream"));

            StringBuilder fullText = new StringBuilder();
            provider().stream(List.of(Message.user("Hi")), null, event -> {
                if ("text_delta".equals(event.type()) && event.text() != null) {
                    fullText.append(event.text());
                }
            });
            assertEquals("Hello world", fullText.toString());
        }
        @Test
        void skipTruncatedToolCallArguments() {
            String sse = """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"Write","arguments":"{\\"path\\":\\"/tmp/test.txt\\",\\"content\\":\\""}}]}}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"truncated"}}]}}]}

                    data: {"choices":[{"delta":{},"finish_reason":"length"}]}

                    data: [DONE]

                    """;
            server.enqueue(new MockResponse()
                    .setBody(sse)
                    .addHeader("Content-Type", "text/event-stream"));

            List<StreamEvent> events = collectEvents(provider());

            assertEquals("message_stop", events.get(events.size() - 1).type());
            ChatResponse response = events.get(events.size() - 1).response();
            assertNotNull(response);
            assertEquals("max_tokens", response.stopReason());
            assertTrue(response.content().stream().noneMatch(b -> b instanceof com.aicode.agent.llm.ToolUseBlock));
        }

        @Test
        void skipMalformedToolCallArgumentsWithoutLengthFinishReason() {
            String sse = """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"Write","arguments":"{\\"path\\":\\"/tmp/test.txt\\",\\"content\\":\\""}}]}}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"truncated"}}]}}]}

                    data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                    data: [DONE]

                    """;
            server.enqueue(new MockResponse()
                    .setBody(sse)
                    .addHeader("Content-Type", "text/event-stream"));

            List<StreamEvent> events = collectEvents(provider());
            ChatResponse response = events.get(events.size() - 1).response();
            assertNotNull(response);
            assertEquals("max_tokens", response.stopReason());
            assertTrue(response.content().stream().noneMatch(b -> b instanceof com.aicode.agent.llm.ToolUseBlock));
        }

        @Test
        void assembleCompleteToolCallArguments() {
            String sse = """
                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_1","function":{"name":"Read","arguments":"{\\"path\\":"}}]}}]}

                    data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\\"/tmp/a.txt\\"}"}}]}}]}

                    data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

                    data: [DONE]

                    """;
            server.enqueue(new MockResponse()
                    .setBody(sse)
                    .addHeader("Content-Type", "text/event-stream"));

            List<StreamEvent> events = collectEvents(provider());
            ChatResponse response = events.get(events.size() - 1).response();
            assertNotNull(response);
            assertEquals("tool_use", response.stopReason());
            assertEquals(1, response.content().stream()
                    .filter(b -> b instanceof com.aicode.agent.llm.ToolUseBlock)
                    .count());
        }
    }
}

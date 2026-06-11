package com.aicode.agent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * LLM provider for Anthropic Claude models.
 */
public class AnthropicProvider implements LLMProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    private final OkHttpClient client;
    private final String apiKey;
    private final String model;

    public record Config(String apiKey, String model) {
        public Config(String apiKey) {
            this(apiKey, "claude-sonnet-4-20250514");
        }
    }

    public AnthropicProvider(Config config) {
        this(config, new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build());
    }

    public AnthropicProvider(Config config, OkHttpClient client) {
        this.apiKey = config.apiKey();
        this.model = config.model();
        this.client = client;
    }

    @Override
    public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            ChatOptions opts = options != null ? options : new ChatOptions();
            try {
                ObjectNode body = MAPPER.createObjectNode();
                body.put("model", model);
                body.put("max_tokens", opts.maxTokens() != null ? opts.maxTokens() : 4096);

                if (opts.system() != null && !opts.system().isEmpty()) {
                    body.put("system", opts.system());
                }

                ArrayNode msgArray = body.putArray("messages");
                for (Message m : messages) {
                    ObjectNode msg = msgArray.addObject();
                    msg.put("role", m.role());
                    msg.set("content", MAPPER.valueToTree(formatContent(m)));
                }

                if (opts.tools() != null && !opts.tools().isEmpty()) {
                    ArrayNode tools = body.putArray("tools");
                    for (Tool tool : opts.tools()) {
                        ObjectNode t = tools.addObject();
                        t.put("name", tool.name());
                        t.put("description", tool.description());
                        t.set("input_schema", MAPPER.valueToTree(tool.inputSchema()));
                    }
                }

                Request request = new Request.Builder()
                        .url(API_URL)
                        .addHeader("x-api-key", apiKey)
                        .addHeader("anthropic-version", "2023-06-01")
                        .addHeader("Content-Type", "application/json")
                        .post(RequestBody.create(body.toString(), JSON))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        String err = response.body() != null ? response.body().string() : "Unknown error";
                        throw new LLMHttpException(response.code(), "API error " + response.code() + ": " + err);
                    }
                    JsonNode root = MAPPER.readTree(response.body().string());
                    return parseResponse(root);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public void stream(List<Message> messages, ChatOptions options, Consumer<StreamEvent> consumer) {
        ChatOptions opts = options != null ? options : new ChatOptions();
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", opts.maxTokens() != null ? opts.maxTokens() : 4096);
            body.put("stream", true);

            if (opts.system() != null && !opts.system().isEmpty()) {
                body.put("system", opts.system());
            }

            ArrayNode msgArray = body.putArray("messages");
            for (Message m : messages) {
                ObjectNode msg = msgArray.addObject();
                msg.put("role", m.role());
                msg.set("content", MAPPER.valueToTree(formatContent(m)));
            }

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", "2023-06-01")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "Unknown error";
                    throw new LLMHttpException(response.code(), "API error " + response.code() + ": " + err);
                }
                consumer.accept(new StreamEvent("message_start"));
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (ChatOptions.shouldCancel(opts)) {
                            break;
                        }
                        if (!line.startsWith("data: ")) {
                            continue;
                        }
                        JsonNode event = MAPPER.readTree(line.substring(6));
                        String type = event.path("type").asText("");
                        if ("content_block_delta".equals(type)) {
                            JsonNode delta = event.path("delta");
                            if ("text_delta".equals(delta.path("type").asText())) {
                                consumer.accept(new StreamEvent("text_delta", delta.path("text").asText()));
                            }
                        }
                    }
                }
                consumer.accept(new StreamEvent("message_stop"));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    Object formatContent(Message message) {
        if (message.isStringContent()) {
            return message.contentText();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (ContentBlock block : message.contentBlocks()) {
            if (block instanceof TextBlock tb) {
                result.add(Map.of("type", "text", "text", tb.text()));
            } else if (block instanceof ToolUseBlock tub) {
                Map<String, Object> m = new HashMap<>();
                m.put("type", "tool_use");
                m.put("id", tub.id());
                m.put("name", tub.name());
                m.put("input", tub.input());
                result.add(m);
            } else if (block instanceof ToolResultBlock trb) {
                Map<String, Object> m = new HashMap<>();
                m.put("type", "tool_result");
                m.put("tool_use_id", trb.toolUseId());
                m.put("content", trb.content());
                if (trb.isError()) {
                    m.put("is_error", true);
                }
                result.add(m);
            }
        }
        return result;
    }

    private ChatResponse parseResponse(JsonNode root) {
        List<ContentBlock> contentBlocks = new ArrayList<>();
        StringBuilder text = new StringBuilder();

        for (JsonNode block : root.path("content")) {
            String type = block.path("type").asText();
            if ("text".equals(type)) {
                String t = block.path("text").asText();
                contentBlocks.add(new TextBlock(t));
                text.append(t);
            } else if ("tool_use".equals(type)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> input = MAPPER.convertValue(block.path("input"), Map.class);
                contentBlocks.add(new ToolUseBlock(
                        block.path("id").asText(),
                        block.path("name").asText(),
                        input
                ));
            }
        }

        String stopReason = root.path("stop_reason").asText("end_turn");
        JsonNode usage = root.path("usage");
        Map<String, Integer> usageMap = Map.of(
                "input_tokens", usage.path("input_tokens").asInt(0),
                "output_tokens", usage.path("output_tokens").asInt(0)
        );

        return new ChatResponse(contentBlocks, text.toString(), stopReason, usageMap);
    }
}

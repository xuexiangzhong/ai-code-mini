package com.aicode.agent.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * LLM provider for OpenAI-compatible APIs (DeepSeek, Qwen, etc.).
 */
public class OpenAICompatibleProvider implements LLMProvider {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    /** DashScope compatible-mode expects {@code baseUrl + /v1/chat/completions}; other URLs are used as-is. */
    private static final String DASHSCOPE_COMPATIBLE_MODE_BASE = "https://dashscope.aliyuncs.com/compatible-mode";

    private final OkHttpClient client;
    private final String apiKey;
    private final String chatCompletionsUrl;
    private final String model;

    public record Config(String apiKey, String baseUrl, String model) {}

    public OpenAICompatibleProvider(Config config) {
        this(config, new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build());
    }

    public OpenAICompatibleProvider(Config config, OkHttpClient client) {
        this.apiKey = config.apiKey();
        this.chatCompletionsUrl = resolveChatCompletionsUrl(config.baseUrl());
        this.model = config.model();
        this.client = client;
    }

    public static String resolveChatCompletionsUrl(String baseUrl) {
        String normalized = baseUrl.replaceAll("/+$", "");
        if (DASHSCOPE_COMPATIBLE_MODE_BASE.equals(normalized)) {
            return normalized + "/v1/chat/completions";
        }
        return normalized;
    }

    @Override
    public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
        return CompletableFuture.supplyAsync(() -> {
            ChatOptions opts = options != null ? options : new ChatOptions();
            try {
                ObjectNode body = MAPPER.createObjectNode();
                body.put("model", model);
                body.put("max_tokens", opts.maxTokens() != null ? opts.maxTokens() : 4096);
                body.set("messages", MAPPER.valueToTree(formatMessages(messages, opts.system())));

                if (opts.tools() != null && !opts.tools().isEmpty()) {
                    ArrayNode tools = body.putArray("tools");
                    for (Tool tool : opts.tools()) {
                        ObjectNode fn = MAPPER.createObjectNode();
                        fn.put("type", "function");
                        ObjectNode function = fn.putObject("function");
                        function.put("name", tool.name());
                        function.put("description", tool.description());
                        function.set("parameters", MAPPER.valueToTree(tool.inputSchema()));
                        tools.add(fn);
                    }
                }

                Request request = new Request.Builder()
                        .url(chatCompletionsUrl)
                        .addHeader("Authorization", "Bearer " + apiKey)
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
    public void stream(List<Message> messages, ChatOptions options, java.util.function.Consumer<StreamEvent> consumer) {
        ChatOptions opts = options != null ? options : new ChatOptions();
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("model", model);
            body.put("stream", true);
            body.put("max_tokens", opts.maxTokens() != null ? opts.maxTokens() : 4096);
            body.set("messages", MAPPER.valueToTree(formatMessages(messages, opts.system())));

            if (opts.tools() != null && !opts.tools().isEmpty()) {
                ArrayNode tools = body.putArray("tools");
                for (Tool tool : opts.tools()) {
                    ObjectNode fn = MAPPER.createObjectNode();
                    fn.put("type", "function");
                    ObjectNode function = fn.putObject("function");
                    function.put("name", tool.name());
                    function.put("description", tool.description());
                    function.set("parameters", MAPPER.valueToTree(tool.inputSchema()));
                    tools.add(fn);
                }
            }

            Request request = new Request.Builder()
                    .url(chatCompletionsUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body.toString(), JSON))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String err = response.body() != null ? response.body().string() : "Unknown error";
                    throw new LLMHttpException(response.code(), "API error " + response.code() + ": " + err);
                }
                consumer.accept(new StreamEvent("message_start"));
                StringBuilder textContent = new StringBuilder();
                Map<Integer, ToolCallAccumulator> toolCalls = new HashMap<>();
                String finishReason = "stop";
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
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }
                        JsonNode chunk = MAPPER.readTree(data);
                        JsonNode choices = chunk.path("choices");
                        if (!choices.isArray() || choices.isEmpty()) {
                            continue;
                        }
                        JsonNode choice = choices.get(0);
                        JsonNode delta = choice.path("delta");
                        if (delta.has("content") && !delta.get("content").isNull()) {
                            String piece = delta.get("content").asText();
                            textContent.append(piece);
                            consumer.accept(new StreamEvent("text_delta", piece));
                        }
                        if (delta.has("tool_calls") && delta.get("tool_calls").isArray()) {
                            for (JsonNode tc : delta.get("tool_calls")) {
                                int index = tc.path("index").asInt(0);
                                ToolCallAccumulator acc = toolCalls.computeIfAbsent(index, k -> new ToolCallAccumulator());
                                if (tc.has("id") && !tc.get("id").isNull()) {
                                    acc.id = tc.get("id").asText();
                                }
                                JsonNode fn = tc.path("function");
                                if (fn.has("name") && !fn.get("name").isNull()) {
                                    acc.name = fn.get("name").asText();
                                }
                                if (fn.has("arguments") && !fn.get("arguments").isNull()) {
                                    acc.arguments.append(fn.get("arguments").asText());
                                }
                            }
                        }
                        JsonNode finish = choice.path("finish_reason");
                        if (!finish.isNull() && !finish.asText("").isEmpty()) {
                            finishReason = finish.asText();
                        }
                    }
                }
                ChatResponse assembled = buildStreamResponse(textContent.toString(), toolCalls, finishReason);
                consumer.accept(new StreamEvent("message_stop", textContent.toString(), assembled));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ChatResponse buildStreamResponse(
            String text,
            Map<Integer, ToolCallAccumulator> toolCalls,
            String finishReason
    ) throws IOException {
        List<ContentBlock> contentBlocks = new ArrayList<>();
        if (!text.isEmpty()) {
            contentBlocks.add(new TextBlock(text));
        }
        boolean hasValidToolCalls = false;
        for (ToolCallAccumulator acc : toolCalls.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList()) {
            if (acc.name == null) {
                continue;
            }
            if ("length".equals(finishReason)) {
                continue;
            }
            Optional<Map<String, Object>> input = tryParseToolArguments(acc.arguments.toString(), acc.name);
            if (input.isEmpty()) {
                continue;
            }
            hasValidToolCalls = true;
            contentBlocks.add(new ToolUseBlock(
                    acc.id != null ? acc.id : "call_" + acc.name,
                    acc.name,
                    input.get()
            ));
        }

        String stopReason = resolveStopReason(finishReason, hasValidToolCalls);

        return new ChatResponse(
                contentBlocks,
                text,
                stopReason,
                Map.of("input_tokens", 0, "output_tokens", 0)
        );
    }

    private static final class ToolCallAccumulator {
        String id;
        String name;
        final StringBuilder arguments = new StringBuilder();
    }

    private ChatResponse parseResponse(JsonNode root) throws IOException {
        JsonNode choice = root.path("choices").get(0);
        JsonNode message = choice.path("message");

        List<ContentBlock> contentBlocks = new ArrayList<>();
        if (message.has("content") && !message.get("content").isNull()) {
            contentBlocks.add(new TextBlock(message.get("content").asText()));
        }
        boolean hasValidToolCalls = false;
        if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
            for (JsonNode tc : message.get("tool_calls")) {
                JsonNode fn = tc.path("function");
                String toolName = fn.path("name").asText("");
                Optional<Map<String, Object>> input = tryParseToolArguments(fn.path("arguments").asText(), toolName);
                if (input.isEmpty()) {
                    continue;
                }
                hasValidToolCalls = true;
                contentBlocks.add(new ToolUseBlock(
                        tc.path("id").asText(),
                        toolName,
                        input.get()
                ));
            }
        }

        String finishReason = choice.path("finish_reason").asText("");
        String stopReason = resolveStopReason(finishReason, hasValidToolCalls);

        JsonNode usage = root.path("usage");
        Map<String, Integer> usageMap = Map.of(
                "input_tokens", usage.path("prompt_tokens").asInt(0),
                "output_tokens", usage.path("completion_tokens").asInt(0)
        );

        String text = message.has("content") && !message.get("content").isNull()
                ? message.get("content").asText() : "";

        return new ChatResponse(contentBlocks, text, stopReason, usageMap);
    }

    private static String resolveStopReason(String finishReason, boolean hasValidToolCalls) {
        if ("tool_calls".equals(finishReason) && hasValidToolCalls) {
            return "tool_use";
        }
        return switch (finishReason) {
            case "stop" -> "end_turn";
            default -> "max_tokens";
        };
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> tryParseToolArguments(String argumentsJson, String toolName) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Optional.of(Map.of());
        }
        try {
            return Optional.of(MAPPER.readValue(argumentsJson, Map.class));
        } catch (JsonProcessingException e) {
            System.err.printf(
                    "Warning: failed to parse tool arguments for %s (length=%d, likely truncated): %s%n",
                    toolName,
                    argumentsJson.length(),
                    e.getOriginalMessage()
            );
            return Optional.empty();
        }
    }

    List<Map<String, Object>> formatMessages(List<Message> messages, String system) {
        List<Map<String, Object>> formatted = new ArrayList<>();
        if (system != null && !system.isEmpty()) {
            formatted.add(Map.of("role", "system", "content", system));
        }
        for (Message m : messages) {
            if (m.isStringContent()) {
                formatted.add(Map.of("role", m.role(), "content", m.contentText()));
                continue;
            }
            List<ContentBlock> blocks = m.contentBlocks();
            if ("assistant".equals(m.role())) {
                StringBuilder text = new StringBuilder();
                List<ToolUseBlock> toolUses = new ArrayList<>();
                for (ContentBlock block : blocks) {
                    if (block instanceof TextBlock tb) {
                        text.append(tb.text());
                    } else if (block instanceof ToolUseBlock tub) {
                        toolUses.add(tub);
                    }
                }
                Map<String, Object> msg = new HashMap<>();
                msg.put("role", "assistant");
                msg.put("content", text.isEmpty() ? null : text.toString());
                if (!toolUses.isEmpty()) {
                    List<Map<String, Object>> toolCalls = new ArrayList<>();
                    for (ToolUseBlock t : toolUses) {
                        Map<String, Object> tc = new HashMap<>();
                        tc.put("id", t.id());
                        tc.put("type", "function");
                        Map<String, Object> fn = new HashMap<>();
                        fn.put("name", t.name());
                        try {
                            fn.put("arguments", MAPPER.writeValueAsString(t.input()));
                        } catch (IOException e) {
                            fn.put("arguments", "{}");
                        }
                        tc.put("function", fn);
                        toolCalls.add(tc);
                    }
                    msg.put("tool_calls", toolCalls);
                }
                formatted.add(msg);
            } else {
                for (ContentBlock block : blocks) {
                    if (block instanceof ToolResultBlock trb) {
                        formatted.add(Map.of(
                                "role", "tool",
                                "tool_call_id", trb.toolUseId(),
                                "content", trb.content()
                        ));
                    }
                }
            }
        }
        return formatted;
    }
}

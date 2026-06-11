package com.aicode.agent;

import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.LLMHelpers;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.OutputTokenLimits;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.Tool;
import com.aicode.agent.llm.ToolUseBlock;
import com.aicode.app.event.AgentEvent;
import com.aicode.app.event.AgentEventListener;
import com.aicode.app.session.RunCancellation;
import com.aicode.app.session.RunCancelledException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Agent loop: send user message to LLM, execute tool calls,
 * feed results back, repeat until LLM stops calling tools.
 */
public final class Agent {
    public static final int DEFAULT_MAX_ITERATIONS = 10;

    @FunctionalInterface
    public interface ToolExecutor {
        CompletableFuture<String> execute(String name, Map<String, Object> input);
    }

    public record AgentConfig(
            LLMProvider provider,
            String system,
            List<Tool> tools,
            ToolExecutor executeTool,
            int maxIterations,
            OutputTokenLimits outputLimits,
            boolean parallelToolCalls,
            RunCancellation cancellation
    ) {
        public AgentConfig(
                LLMProvider provider,
                String system,
                List<Tool> tools,
                ToolExecutor executeTool
        ) {
            this(provider, system, tools, executeTool, DEFAULT_MAX_ITERATIONS, OutputTokenLimits.defaults(), false, null);
        }

        public AgentConfig(
                LLMProvider provider,
                String system,
                List<Tool> tools,
                ToolExecutor executeTool,
                int maxIterations,
                OutputTokenLimits outputLimits,
                boolean parallelToolCalls
        ) {
            this(provider, system, tools, executeTool, maxIterations, outputLimits, parallelToolCalls, null);
        }
    }

    public record ToolCallRecord(String name, Map<String, Object> input, String result) {}

    public record AgentResult(
            String text,
            List<ToolCallRecord> toolCalls,
            int iterations,
            int inputTokens,
            int outputTokens
    ) {}

    private Agent() {}

    public static CompletableFuture<AgentResult> runAgent(AgentConfig config, String userMessage) {
        return runAgent(config, List.of(Message.user(userMessage)));
    }

    public static CompletableFuture<AgentResult> runAgent(AgentConfig config, List<Message> messages) {
        List<ToolCallRecord> toolCalls = new ArrayList<>();
        int[] totalInput = {0};
        int[] totalOutput = {0};
        return runIteration(config, new ArrayList<>(messages), toolCalls, totalInput, totalOutput, 0, null, 0);
    }

    public static CompletableFuture<AgentResult> runAgentStream(
            AgentConfig config,
            List<Message> messages,
            AgentEventListener listener
    ) {
        List<ToolCallRecord> toolCalls = new ArrayList<>();
        int[] totalInput = {0};
        int[] totalOutput = {0};
        AgentEventListener events = listener != null ? listener : AgentEventListener.NOOP;
        return runIteration(config, new ArrayList<>(messages), toolCalls, totalInput, totalOutput, 0, events, 0);
    }

    private static CompletableFuture<AgentResult> runIteration(
            AgentConfig config,
            List<Message> messages,
            List<ToolCallRecord> toolCalls,
            int[] totalInput,
            int[] totalOutput,
            int iteration,
            AgentEventListener listener,
            int outputRetryAttempt
    ) {
        if (isCancelled(config)) {
            return cancelledFuture(config, toolCalls, totalInput[0], totalOutput[0], iteration, listener);
        }

        if (iteration >= config.maxIterations()) {
            emitDone(listener, config.maxIterations(), totalInput[0], totalOutput[0]);
            return CompletableFuture.completedFuture(new AgentResult(
                    "(max iterations reached)",
                    List.copyOf(toolCalls),
                    config.maxIterations(),
                    totalInput[0],
                    totalOutput[0]
            ));
        }

        int outputLimit = config.outputLimits().limitForAttempt(outputRetryAttempt);
        ChatOptions options = new ChatOptions(
                config.system(),
                outputLimit,
                config.tools(),
                () -> isCancelled(config)
        );

        if (listener != null) {
            return streamChat(
                    config, messages, options, toolCalls, totalInput, totalOutput,
                    iteration, listener, outputRetryAttempt
            );
        }

        return config.provider().chat(messages, options).thenCompose(response ->
                handleResponse(
                        config, messages, toolCalls, totalInput, totalOutput,
                        iteration, listener, response, outputRetryAttempt, options
                ));
    }

    private static CompletableFuture<AgentResult> streamChat(
            AgentConfig config,
            List<Message> messages,
            ChatOptions options,
            List<ToolCallRecord> toolCalls,
            int[] totalInput,
            int[] totalOutput,
            int iteration,
            AgentEventListener listener,
            int outputRetryAttempt
    ) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder accumulated = new StringBuilder();
            java.util.concurrent.atomic.AtomicReference<ChatResponse> responseRef =
                    new java.util.concurrent.atomic.AtomicReference<>();
            try {
                config.provider().stream(messages, options, event -> {
                    if (ChatOptions.shouldCancel(options)) {
                        trackPartial(config, accumulated.toString());
                        throw new RunCancelledException(accumulated.toString());
                    }
                    if ("text_delta".equals(event.type()) && event.text() != null) {
                        accumulated.append(event.text());
                        trackPartial(config, accumulated.toString());
                    }
                    if ("message_stop".equals(event.type())) {
                        if (event.response() != null) {
                            responseRef.set(event.response());
                        }
                    }
                });
            } catch (RunCancelledException e) {
                trackPartial(config, e.partialText());
                throw e;
            }
            if (ChatOptions.shouldCancel(options)) {
                trackPartial(config, accumulated.toString());
                throw new RunCancelledException(accumulated.toString());
            }
            ChatResponse response = responseRef.get();
            if (response == null) {
                String text = accumulated.toString();
                response = new ChatResponse(
                        text.isEmpty() ? List.of() : List.of(new TextBlock(text)),
                        text,
                        "end_turn",
                        Map.of("input_tokens", 0, "output_tokens", 0)
                );
            }
            return response;
        }).thenCompose(response -> {
            if (shouldRetryOutput(config, response, outputRetryAttempt)) {
                int currentLimit = config.outputLimits().limitForAttempt(outputRetryAttempt);
                int nextLimit = config.outputLimits().limitForAttempt(outputRetryAttempt + 1);
                listener.onEvent(new AgentEvent.OutputTruncated(
                        OutputTokenLimits.retryMessage(currentLimit, nextLimit),
                        currentLimit,
                        true
                ));
                return runIteration(
                        config, messages, toolCalls, totalInput, totalOutput,
                        iteration, listener, outputRetryAttempt + 1
                );
            }
            String text = response.text() != null && !response.text().isBlank()
                    ? response.text()
                    : accumulatedText(response);
            if (!text.isEmpty()) {
                listener.onEvent(new AgentEvent.TextDelta(text));
                listener.onEvent(new AgentEvent.TextDone(text));
            }
            return handleResponse(
                    config, messages, toolCalls, totalInput, totalOutput,
                    iteration, listener, response, outputRetryAttempt, options
            );
        }).exceptionallyCompose(error -> {
            RunCancelledException cancelled = findCancelled(error);
            if (cancelled != null) {
                return cancelledFuture(
                        config, toolCalls, totalInput[0], totalOutput[0], iteration, listener
                );
            }
            if (error instanceof RuntimeException runtime) {
                return CompletableFuture.failedFuture(runtime);
            }
            return CompletableFuture.failedFuture(error);
        });
    }

    private static String accumulatedText(ChatResponse response) {
        return LLMHelpers.extractText(response.content());
    }

    private static CompletableFuture<AgentResult> handleResponse(
            AgentConfig config,
            List<Message> messages,
            List<ToolCallRecord> toolCalls,
            int[] totalInput,
            int[] totalOutput,
            int iteration,
            AgentEventListener listener,
            ChatResponse response,
            int outputRetryAttempt,
            ChatOptions options
    ) {
        if (shouldRetryOutput(config, response, outputRetryAttempt)) {
            int currentLimit = config.outputLimits().limitForAttempt(outputRetryAttempt);
            int nextLimit = config.outputLimits().limitForAttempt(outputRetryAttempt + 1);
            if (listener != null) {
                listener.onEvent(new AgentEvent.OutputTruncated(
                        OutputTokenLimits.retryMessage(currentLimit, nextLimit),
                        currentLimit,
                        true
                ));
            }
            return runIteration(
                    config, messages, toolCalls, totalInput, totalOutput,
                    iteration, listener, outputRetryAttempt + 1
            );
        }

        totalInput[0] += response.inputTokens();
        totalOutput[0] += response.outputTokens();

        if (!"tool_use".equals(response.stopReason())) {
            String text = LLMHelpers.extractText(response.content());
            if ("max_tokens".equals(response.stopReason())) {
                int limit = options.maxTokens() != null
                        ? options.maxTokens()
                        : config.outputLimits().limitForAttempt(outputRetryAttempt);
                String warning = OutputTokenLimits.exhaustedMessage(limit);
                if (listener != null) {
                    listener.onEvent(new AgentEvent.OutputTruncated(warning, limit, false));
                }
                if (!text.isBlank()) {
                    text = text + "\n\n⚠ " + warning;
                } else {
                    text = "⚠ " + warning;
                }
            }
            emitDone(listener, iteration + 1, totalInput[0], totalOutput[0]);
            return CompletableFuture.completedFuture(new AgentResult(
                    text,
                    List.copyOf(toolCalls),
                    iteration + 1,
                    totalInput[0],
                    totalOutput[0]
            ));
        }

        if (isCancelled(config)) {
            return cancelledFuture(config, toolCalls, totalInput[0], totalOutput[0], iteration, listener);
        }

        List<ToolUseBlock> uses = LLMHelpers.extractToolUses(response.content());
        messages.add(Message.assistant(new ArrayList<>(response.content())));

        CompletableFuture<List<ContentBlock>> resultsFuture;
        if (config.parallelToolCalls() && uses.size() > 1) {
            List<CompletableFuture<String>> resultFutures = new ArrayList<>();
            for (ToolUseBlock use : uses) {
                if (isCancelled(config)) {
                    return cancelledFuture(config, toolCalls, totalInput[0], totalOutput[0], iteration, listener);
                }
                resultFutures.add(config.executeTool().execute(use.name(), use.input()));
            }
            resultsFuture = CompletableFuture.allOf(resultFutures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> {
                        List<ContentBlock> results = new ArrayList<>();
                        for (int i = 0; i < uses.size(); i++) {
                            String result = resultFutures.get(i).join();
                            ToolUseBlock use = uses.get(i);
                            toolCalls.add(new ToolCallRecord(use.name(), use.input(), result));
                            results.add(LLMHelpers.createToolResult(use.id(), result));
                        }
                        return results;
                    });
        } else {
            CompletableFuture<List<ContentBlock>> chain = CompletableFuture.completedFuture(new ArrayList<>());
            for (ToolUseBlock use : uses) {
                chain = chain.thenCompose(results -> {
                    if (isCancelled(config)) {
                        return CompletableFuture.failedFuture(
                                new RunCancelledException(partialText(config))
                        );
                    }
                    return config.executeTool()
                            .execute(use.name(), use.input())
                            .thenApply(result -> {
                                toolCalls.add(new ToolCallRecord(use.name(), use.input(), result));
                                results.add(LLMHelpers.createToolResult(use.id(), result));
                                return results;
                            });
                });
            }
            resultsFuture = chain;
        }

        return resultsFuture
                .exceptionallyCompose(error -> {
                    RunCancelledException cancelled = findCancelled(error);
                    if (cancelled != null) {
                        return cancelledFuture(
                                config, toolCalls, totalInput[0], totalOutput[0], iteration, listener
                        ).thenApply(r -> List.<ContentBlock>of());
                    }
                    if (error instanceof RuntimeException runtime) {
                        return CompletableFuture.failedFuture(runtime);
                    }
                    return CompletableFuture.failedFuture(error);
                })
                .thenCompose(results -> {
                    if (isCancelled(config)) {
                        return cancelledFuture(
                                config, toolCalls, totalInput[0], totalOutput[0], iteration, listener
                        );
                    }
                    messages.add(Message.userBlocks(results));
                    return runIteration(
                            config, messages, toolCalls, totalInput, totalOutput,
                            iteration + 1, listener, 0
                    );
                });
    }

    private static boolean isCancelled(AgentConfig config) {
        return config.cancellation() != null && config.cancellation().isCancelled();
    }

    private static String partialText(AgentConfig config) {
        return config.cancellation() != null ? config.cancellation().partialText() : "";
    }

    private static void trackPartial(AgentConfig config, String text) {
        if (config.cancellation() != null) {
            config.cancellation().trackPartialText(text);
        }
    }

    private static RunCancelledException findCancelled(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof RunCancelledException cancelled) {
                return cancelled;
            }
            current = current.getCause();
        }
        return null;
    }

    private static CompletableFuture<AgentResult> cancelledFuture(
            AgentConfig config,
            List<ToolCallRecord> toolCalls,
            int totalInput,
            int totalOutput,
            int iteration,
            AgentEventListener listener
    ) {
        String partial = partialText(config);
        if (listener != null) {
            listener.onEvent(new AgentEvent.Cancelled(partial));
            emitDone(listener, iteration, totalInput, totalOutput);
        }
        return CompletableFuture.completedFuture(new AgentResult(
                partial,
                List.copyOf(toolCalls),
                iteration,
                totalInput,
                totalOutput
        ));
    }

    private static boolean shouldRetryOutput(
            AgentConfig config,
            ChatResponse response,
            int outputRetryAttempt
    ) {
        return "max_tokens".equals(response.stopReason())
                && config.outputLimits().canRetry(outputRetryAttempt);
    }

    private static void emitDone(AgentEventListener listener, int iterations, int inputTokens, int outputTokens) {
        if (listener != null) {
            listener.onEvent(new AgentEvent.Done(iterations, inputTokens, outputTokens));
        }
    }
}

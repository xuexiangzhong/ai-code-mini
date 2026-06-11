package com.aicode.agent.llm;

import com.aicode.app.event.AgentEvent;
import com.aicode.app.event.AgentEventListener;
import com.aicode.app.session.RunCancellation;
import com.aicode.app.session.RunCancelledException;

import java.util.List;
import java.util.function.Consumer;

/**
 * Streams LLM text with automatic max_tokens retry using scaled output limits.
 */
public final class StreamingChatHelper {
    private StreamingChatHelper() {}

    public record StreamResult(String text, ChatResponse response) {}

    public static StreamResult streamWithRetry(
            LLMProvider provider,
            List<Message> messages,
            ChatOptions baseOptions,
            OutputTokenLimits limits,
            AgentEventListener listener,
            Consumer<String> onDelta
    ) {
        return streamWithRetry(provider, messages, baseOptions, limits, listener, onDelta, null);
    }

    public static StreamResult streamWithRetry(
            LLMProvider provider,
            List<Message> messages,
            ChatOptions baseOptions,
            OutputTokenLimits limits,
            AgentEventListener listener,
            Consumer<String> onDelta,
            RunCancellation cancellation
    ) {
        AgentEventListener events = listener != null ? listener : AgentEventListener.NOOP;
        for (int attempt = 0; ; attempt++) {
            if (cancellation != null && cancellation.isCancelled()) {
                throw new RunCancelledException(cancellation.partialText());
            }
            int outputLimit = limits.limitForAttempt(attempt);
            ChatOptions options = baseOptions.withMaxTokens(outputLimit);
            if (cancellation != null) {
                options = options.withCancelCheck(cancellation::isCancelled);
            }
            StreamResult result = streamOnce(provider, messages, options, cancellation);
            if (!"max_tokens".equals(result.response().stopReason())) {
                publishText(result.text(), events, onDelta);
                return result;
            }
            if (limits.canRetry(attempt)) {
                int nextLimit = limits.limitForAttempt(attempt + 1);
                events.onEvent(new AgentEvent.OutputTruncated(
                        OutputTokenLimits.retryMessage(outputLimit, nextLimit),
                        outputLimit,
                        true
                ));
                continue;
            }
            String warning = OutputTokenLimits.exhaustedMessage(outputLimit);
            events.onEvent(new AgentEvent.OutputTruncated(warning, outputLimit, false));
            String text = result.text();
            if (text.isBlank()) {
                text = "⚠ " + warning;
            } else {
                text = text + "\n\n⚠ " + warning;
            }
            publishText(text, events, onDelta);
            return new StreamResult(text, result.response());
        }
    }

    private static void publishText(String text, AgentEventListener events, Consumer<String> onDelta) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (onDelta != null) {
            onDelta.accept(text);
        }
        events.onEvent(new AgentEvent.TextDelta(text));
        events.onEvent(new AgentEvent.TextDone(text));
    }

    private static StreamResult streamOnce(
            LLMProvider provider,
            List<Message> messages,
            ChatOptions options,
            RunCancellation cancellation
    ) {
        StringBuilder fullText = new StringBuilder();
        java.util.concurrent.atomic.AtomicReference<ChatResponse> responseRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        provider.stream(messages, options, event -> {
            if (ChatOptions.shouldCancel(options)) {
                if (cancellation != null) {
                    cancellation.trackPartialText(fullText.toString());
                }
                throw new RunCancelledException(fullText.toString());
            }
            if ("text_delta".equals(event.type()) && event.text() != null) {
                fullText.append(event.text());
                if (cancellation != null) {
                    cancellation.trackPartialText(fullText.toString());
                }
            }
            if ("message_stop".equals(event.type()) && event.response() != null) {
                responseRef.set(event.response());
            }
        });
        if (ChatOptions.shouldCancel(options)) {
            throw new RunCancelledException(fullText.toString());
        }
        ChatResponse response = responseRef.get();
        if (response == null) {
            String text = fullText.toString();
            response = new ChatResponse(
                    text.isEmpty() ? List.of() : List.of(new TextBlock(text)),
                    text,
                    "end_turn",
                    java.util.Map.of("input_tokens", 0, "output_tokens", 0)
            );
        }
        String text = fullText.isEmpty() ? response.text() : fullText.toString();
        return new StreamResult(text, response);
    }
}

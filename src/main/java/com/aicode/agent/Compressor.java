package com.aicode.agent;

import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Conversation compression by summarizing old messages.
 */
public final class Compressor {
    public record CompressorConfig(
            LLMProvider provider,
            int maxTokens,
            int keepRecentMessages,
            int summaryMaxTokens
    ) {
        public CompressorConfig(LLMProvider provider) {
            this(provider, 50_000, 6, 1024);
        }
    }

    public record CompressResult(
            List<Message> messages,
            boolean compressed,
            int originalCount,
            int compressedCount,
            int summaryTokens
    ) {}

    private Compressor() {}

    private static String formatContent(Message message) {
        if (message.isStringContent()) {
            return message.contentText();
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : message.contentBlocks()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.text()).append("\n");
            } else if (block instanceof ToolUseBlock tub) {
                sb.append("[Tool call: ").append(tub.name()).append("]\n");
            } else if (block instanceof ToolResultBlock trb) {
                String content = trb.content();
                sb.append("[Tool result: ")
                        .append(content.substring(0, Math.min(200, content.length())))
                        .append("]\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    public static CompletableFuture<String> summarizeMessages(
            LLMProvider provider,
            List<Message> messages,
            int maxTokens
    ) {
        String formatted = String.join("\n\n", messages.stream()
                .map(m -> m.role() + ": " + formatContent(m))
                .toList());

        Message userMsg = Message.user(
                "Summarize this conversation concisely. Focus on: what the user asked, "
                        + "what tools were used, what was accomplished, and any important decisions "
                        + "or findings.\n\n" + formatted
        );

        ChatOptions options = new ChatOptions(
                "You are a conversation summarizer. Produce a concise summary that "
                        + "captures the key information needed to continue the conversation. "
                        + "Do not include pleasantries or meta-commentary.",
                maxTokens,
                null
        );

        return provider.chat(List.of(userMsg), options).thenApply(response -> response.text());
    }

    public static CompletableFuture<CompressResult> compressConversation(
            CompressorConfig config,
            List<Message> messages
    ) {
        int totalTokens = messages.stream().mapToInt(TokenCounter::estimateMessageTokens).sum();

        if (totalTokens <= config.maxTokens() || messages.size() <= config.keepRecentMessages()) {
            return CompletableFuture.completedFuture(new CompressResult(
                    List.copyOf(messages), false, messages.size(), messages.size(), 0
            ));
        }

        int splitIndex = messages.size() - config.keepRecentMessages();
        List<Message> oldMessages = messages.subList(0, splitIndex);
        List<Message> recentMessages = messages.subList(splitIndex, messages.size());

        return summarizeMessages(config.provider(), oldMessages, config.summaryMaxTokens())
                .thenApply(summary -> {
                    Message summaryMessage = Message.user("[Previous conversation summary]\n" + summary);
                    List<Message> result = new ArrayList<>();
                    result.add(summaryMessage);
                    result.addAll(recentMessages);
                    return new CompressResult(
                            result,
                            true,
                            messages.size(),
                            result.size(),
                            TokenCounter.estimateMessageTokens(summaryMessage)
                    );
                });
    }

    public static boolean needsCompression(List<Message> messages, int maxTokens) {
        int total = messages.stream().mapToInt(TokenCounter::estimateMessageTokens).sum();
        return total > maxTokens;
    }
}

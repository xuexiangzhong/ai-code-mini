package com.aicode.agent;

import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.Tool;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Token estimation utilities for context budget management.
 */
public final class TokenCounter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final Map<String, Integer> MODEL_CONTEXT_LIMITS = Map.ofEntries(
            Map.entry("claude-sonnet-4-20250514", 200_000),
            Map.entry("claude-haiku-4-20250414", 200_000),
            Map.entry("claude-3-5-sonnet-20241022", 200_000),
            Map.entry("gpt-4o", 128_000),
            Map.entry("gpt-4o-mini", 128_000),
            Map.entry("deepseek-chat", 64_000),
            Map.entry("deepseek-coder", 128_000)
    );

    private TokenCounter() {}

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjkChars = 0;
        int otherChars = 0;
        for (int i = 0; i < text.length(); i++) {
            int code = text.codePointAt(i);
            if (code > 0xFFFF) {
                i++;
            }
            if ((0x4E00 <= code && code <= 0x9FFF)
                    || (0x3000 <= code && code <= 0x303F)
                    || (0x3040 <= code && code <= 0x30FF)
                    || (0xFF00 <= code && code <= 0xFFEF)) {
                cjkChars++;
            } else {
                otherChars++;
            }
        }
        return (int) Math.ceil(cjkChars / 2.0) + (int) Math.ceil(otherChars / 4.0);
    }

    private static int estimateBlockTokens(ContentBlock block) {
        if (block instanceof TextBlock tb) {
            return estimateTokens(tb.text());
        } else if (block instanceof ToolUseBlock tub) {
            try {
                return estimateTokens(tub.name()) + estimateTokens(MAPPER.writeValueAsString(tub.input()));
            } catch (Exception e) {
                return estimateTokens(tub.name());
            }
        } else if (block instanceof ToolResultBlock trb) {
            return estimateTokens(trb.content());
        }
        return 0;
    }

    public static int estimateMessageTokens(Message message) {
        int overhead = 4;
        if (message.isStringContent()) {
            return overhead + estimateTokens(message.contentText());
        }
        int total = overhead;
        for (ContentBlock block : message.contentBlocks()) {
            total += estimateBlockTokens(block);
        }
        return total;
    }

    public static int estimateConversationTokens(List<Message> messages, String system, List<Tool> tools) {
        int total = 0;
        if (system != null && !system.isEmpty()) {
            total += estimateTokens(system);
        }
        if (tools != null) {
            for (Tool tool : tools) {
                try {
                    total += estimateTokens(tool.name())
                            + estimateTokens(tool.description())
                            + estimateTokens(MAPPER.writeValueAsString(tool.inputSchema()));
                } catch (Exception ignored) {
                    total += estimateTokens(tool.name());
                }
            }
        }
        for (Message msg : messages) {
            total += estimateMessageTokens(msg);
        }
        return total;
    }

    public static Integer getModelContextLimit(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        return com.aicode.app.config.ModelContextLimits.forModel(model);
    }

    public record ContextBudget(int maxContextTokens, int reservedForResponse) {
        public static final ContextBudget DEFAULT = new ContextBudget(64_000, 4096);
    }

    public static int remainingBudget(ContextBudget budget, int usedTokens) {
        return Math.max(0, budget.maxContextTokens() - budget.reservedForResponse() - usedTokens);
    }

    public static boolean isOverBudget(ContextBudget budget, int usedTokens) {
        return usedTokens >= budget.maxContextTokens() - budget.reservedForResponse();
    }
}

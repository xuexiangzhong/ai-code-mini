package com.aicode.agent;

import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.LLMHelpers;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Ensures every assistant {@code tool_use} has a matching tool result before messages
 * are sent to OpenAI-compatible APIs.
 */
public final class ToolMessageRepair {
    static final String INCOMPLETE_RESULT =
            "Error: tool execution did not complete (run cancelled or interrupted).";

    private ToolMessageRepair() {}

    public static List<Message> repair(List<Message> messages) {
        if (messages.isEmpty()) {
            return List.of();
        }
        List<Message> repaired = new ArrayList<>(messages.size() + 2);
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (!isToolUseAssistant(message)) {
                repaired.add(message);
                continue;
            }

            Set<String> expectedIds = toolUseIds(message);
            repaired.add(message);

            int j = i + 1;
            List<ToolResultBlock> collected = new ArrayList<>();
            while (j < messages.size() && isToolResultUser(messages.get(j))) {
                for (ContentBlock block : messages.get(j).contentBlocks()) {
                    if (block instanceof ToolResultBlock trb) {
                        collected.add(trb);
                    }
                }
                j++;
            }

            Set<String> receivedIds = new LinkedHashSet<>();
            for (ToolResultBlock trb : collected) {
                receivedIds.add(trb.toolUseId());
            }

            if (!receivedIds.containsAll(expectedIds)) {
                List<ContentBlock> synthetic = new ArrayList<>();
                for (ToolResultBlock trb : collected) {
                    synthetic.add(trb);
                }
                for (String id : expectedIds) {
                    if (!receivedIds.contains(id)) {
                        synthetic.add(LLMHelpers.createToolResult(id, INCOMPLETE_RESULT, true));
                    }
                }
                repaired.add(Message.userBlocks(synthetic));
                i = j - 1;
                continue;
            }

            for (int k = i + 1; k < j; k++) {
                repaired.add(messages.get(k));
            }
            i = j - 1;
        }
        return repaired;
    }

    /**
     * After cancel: keep real results for tools that finished, synthetic errors for the rest.
     *
     * @param toolCallsBeforeTurn {@code toolCalls.size()} before the current assistant tool turn started
     */
    public static void finalizeCancelledToolTurn(
            List<Message> messages,
            List<Agent.ToolCallRecord> toolCalls,
            int toolCallsBeforeTurn
    ) {
        if (messages.isEmpty() || alreadyHasToolResultsForLastAssistant(messages)) {
            return;
        }
        Message assistant = messages.getLast();
        if (!isToolUseAssistant(assistant)) {
            return;
        }

        List<ToolUseBlock> uses = new ArrayList<>();
        for (ContentBlock block : assistant.contentBlocks()) {
            if (block instanceof ToolUseBlock tub) {
                uses.add(tub);
            }
        }
        if (uses.isEmpty()) {
            return;
        }

        int completed = Math.max(0, toolCalls.size() - toolCallsBeforeTurn);
        List<ContentBlock> results = new ArrayList<>(uses.size());
        for (int i = 0; i < uses.size(); i++) {
            ToolUseBlock use = uses.get(i);
            if (i < completed) {
                Agent.ToolCallRecord record = toolCalls.get(toolCallsBeforeTurn + i);
                results.add(LLMHelpers.createToolResult(
                        use.id(),
                        record.result(),
                        Errors.isToolErrorResult(record.result())
                ));
            } else {
                results.add(LLMHelpers.createToolResult(use.id(), INCOMPLETE_RESULT, true));
            }
        }
        messages.add(Message.userBlocks(results));
    }

    public static boolean differs(List<Message> left, List<Message> right) {
        if (left.size() != right.size()) {
            return true;
        }
        for (int i = 0; i < left.size(); i++) {
            if (!messageEqual(left.get(i), right.get(i))) {
                return true;
            }
        }
        return false;
    }

    static boolean alreadyHasToolResultsForLastAssistant(List<Message> messages) {
        if (messages.size() < 2) {
            return false;
        }
        return isToolUseAssistant(messages.get(messages.size() - 2))
                && isToolResultUser(messages.getLast());
    }

    private static boolean messageEqual(Message a, Message b) {
        if (!a.role().equals(b.role())) {
            return false;
        }
        if (a.isStringContent() && b.isStringContent()) {
            return a.contentText().equals(b.contentText());
        }
        if (!a.isStringContent() && !b.isStringContent()) {
            return formatBlocks(a).equals(formatBlocks(b));
        }
        return false;
    }

    private static String formatBlocks(Message message) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : message.contentBlocks()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.text());
            } else if (block instanceof ToolResultBlock trb) {
                sb.append(trb.toolUseId()).append('\0').append(trb.content()).append('\0').append(trb.isError());
            } else if (block instanceof ToolUseBlock tub) {
                sb.append(tub.id()).append('\0').append(tub.name());
            }
        }
        return sb.toString();
    }

    static boolean isToolUseAssistant(Message message) {
        if (!"assistant".equals(message.role()) || message.isStringContent()) {
            return false;
        }
        for (ContentBlock block : message.contentBlocks()) {
            if (block instanceof ToolUseBlock) {
                return true;
            }
        }
        return false;
    }

    static boolean isToolResultUser(Message message) {
        if (!"user".equals(message.role()) || message.isStringContent()) {
            return false;
        }
        for (ContentBlock block : message.contentBlocks()) {
            if (block instanceof ToolResultBlock) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> toolUseIds(Message assistantMessage) {
        Set<String> ids = new LinkedHashSet<>();
        for (ContentBlock block : assistantMessage.contentBlocks()) {
            if (block instanceof ToolUseBlock tub) {
                ids.add(tub.id());
            }
        }
        return ids;
    }
}

package com.aicode.agent;

import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lightweight history compaction before LLM summarization: trims stale tool output,
 * @ attachments, and environment noise from older turns.
 */
public final class HistoryCompactor {
    static final int KEEP_RECENT_USER_TURNS = 3;
    static final int OLD_TOOL_RESULT_MAX_CHARS = 500;
    static final double SOFT_PRUNE_RATIO = 0.45;

    private static final Pattern CODEBASE_BLOCK = Pattern.compile(
            "\\[@Codebase 检索][\\s\\S]*?(\\n\\n|$)", Pattern.MULTILINE);
    private static final Pattern FILE_BLOCK = Pattern.compile(
            "\\[@文件:[^\\]]+][\\s\\S]*?(\\n\\n|$)", Pattern.MULTILINE);
    private static final Pattern FOLDER_BLOCK = Pattern.compile(
            "\\[@文件夹:[^\\]]+][\\s\\S]*?(\\n\\n|$)", Pattern.MULTILINE);
    private static final Pattern SELECTION_BLOCK = Pattern.compile(
            "\\[@选中代码:[^\\]]+][\\s\\S]*?(\\n\\n|$)", Pattern.MULTILINE);
    private static final Pattern ENV_BLOCK = Pattern.compile(
            "<environment>[\\s\\S]*?</environment>\\s*", Pattern.MULTILINE);
    private static final Pattern UNTRUSTED_BLOCK = Pattern.compile(
            "<untrusted_context[^>]*>[\\s\\S]*?</untrusted_context>", Pattern.MULTILINE);

    private HistoryCompactor() {}

    public record CompactResult(List<Message> messages, boolean changed, int tokensBefore, int tokensAfter) {}

    public static CompactResult compact(List<Message> messages, int maxTokens) {
        if (messages.isEmpty()) {
            return new CompactResult(List.of(), false, 0, 0);
        }
        int before = estimateTokens(messages);
        if (before <= maxTokens * SOFT_PRUNE_RATIO) {
            return new CompactResult(List.copyOf(messages), false, before, before);
        }

        int splitIndex = splitIndexForRecentUserTurns(messages, KEEP_RECENT_USER_TURNS);
        if (splitIndex >= messages.size() && before > maxTokens * SOFT_PRUNE_RATIO) {
            splitIndex = splitIndexForRecentUserTurns(messages, 1);
        }

        List<Message> result = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            boolean aggressive = i < splitIndex && !isSummaryMessage(messages.get(i));
            result.add(compactMessage(messages.get(i), aggressive));
        }

        int after = estimateTokens(result);
        boolean changed = !messagesEqual(messages, result);
        return new CompactResult(result, changed, before, after);
    }

    static int splitIndexForRecentUserTurns(List<Message> messages, int keepUserTurns) {
        if (keepUserTurns <= 0) {
            return messages.size();
        }
        int count = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (isUserTurnMessage(messages.get(i))) {
                count++;
                if (count >= keepUserTurns) {
                    return i;
                }
            }
        }
        return messages.size();
    }

    static Message compactMessage(Message message, boolean aggressive) {
        if (!aggressive) {
            return message;
        }
        if (message.isStringContent()) {
            return Message.user(compactUserText(message.contentText()));
        }
        List<ContentBlock> blocks = message.contentBlocks();
        List<ContentBlock> compacted = new ArrayList<>(blocks.size());
        for (ContentBlock block : blocks) {
            if (block instanceof TextBlock tb) {
                compacted.add(new TextBlock(compactUserText(tb.text())));
            } else if (block instanceof ToolResultBlock trb) {
                compacted.add(new ToolResultBlock(
                        trb.toolUseId(),
                        truncateToolResult(trb.content()),
                        trb.isError()
                ));
            } else if (block instanceof ToolUseBlock tub) {
                compacted.add(tub);
            } else {
                compacted.add(block);
            }
        }
        return "assistant".equals(message.role())
                ? Message.assistant(compacted)
                : Message.userBlocks(compacted);
    }

    static String compactUserText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (text.startsWith("[Previous conversation summary]")) {
            return text;
        }
        String stripped = text;
        stripped = ENV_BLOCK.matcher(stripped).replaceAll("");
        stripped = CODEBASE_BLOCK.matcher(stripped).replaceAll("[@Codebase 检索] (历史附件已省略)\n\n");
        stripped = FILE_BLOCK.matcher(stripped).replaceAll("[@文件] (历史附件已省略)\n\n");
        stripped = FOLDER_BLOCK.matcher(stripped).replaceAll("[@文件夹] (历史附件已省略)\n\n");
        stripped = SELECTION_BLOCK.matcher(stripped).replaceAll("[@选中代码] (历史附件已省略)\n\n");
        stripped = UNTRUSTED_BLOCK.matcher(stripped).replaceAll("(附件内容已省略)");
        return stripped.strip();
    }

    static String truncateToolResult(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        if (content.length() <= OLD_TOOL_RESULT_MAX_CHARS) {
            return content;
        }
        return content.substring(0, OLD_TOOL_RESULT_MAX_CHARS)
                + "\n…(历史 tool 输出已截断，共 " + content.length() + " 字符)";
    }

    static boolean isUserTurnMessage(Message message) {
        return "user".equals(message.role())
                && message.isStringContent()
                && !message.contentText().startsWith("[Previous conversation summary]");
    }

    static boolean isSummaryMessage(Message message) {
        return message.isStringContent()
                && message.contentText().startsWith("[Previous conversation summary]");
    }

    private static int estimateTokens(List<Message> messages) {
        return messages.stream().mapToInt(TokenCounter::estimateMessageTokens).sum();
    }

    private static boolean messagesEqual(List<Message> left, List<Message> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int i = 0; i < left.size(); i++) {
            Message a = left.get(i);
            Message b = right.get(i);
            if (!a.role().equals(b.role())) {
                return false;
            }
            if (a.isStringContent() && b.isStringContent()) {
                if (!a.contentText().equals(b.contentText())) {
                    return false;
                }
            } else if (!a.isStringContent() && !b.isStringContent()) {
                if (!formatBlocks(a).equals(formatBlocks(b))) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private static String formatBlocks(Message message) {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : message.contentBlocks()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.text());
            } else if (block instanceof ToolResultBlock trb) {
                sb.append(trb.content());
            } else if (block instanceof ToolUseBlock tub) {
                sb.append(tub.name());
            }
        }
        return sb.toString();
    }
}

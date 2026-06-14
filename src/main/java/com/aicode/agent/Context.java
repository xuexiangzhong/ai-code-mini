package com.aicode.agent;

import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.Tool;
import com.aicode.agent.llm.ToolUseBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Agent scratchpad and context utilities.
 */
public final class Context {
    private Context() {}

    public static class Scratchpad {
        private final List<Map.Entry<String, String>> entries = new ArrayList<>();

        public void set(String key, String value) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).getKey().equals(key)) {
                    entries.set(i, Map.entry(key, value));
                    return;
                }
            }
            entries.add(Map.entry(key, value));
        }

        public String get(String key) {
            for (Map.Entry<String, String> e : entries) {
                if (e.getKey().equals(key)) {
                    return e.getValue();
                }
            }
            return null;
        }

        public boolean delete(String key) {
            return entries.removeIf(e -> e.getKey().equals(key));
        }

        public boolean has(String key) {
            return entries.stream().anyMatch(e -> e.getKey().equals(key));
        }

        public void clear() {
            entries.clear();
        }

        public String format() {
            if (entries.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("## Scratchpad\n");
            for (Map.Entry<String, String> e : entries) {
                sb.append("- **").append(e.getKey()).append("**: ").append(e.getValue()).append("\n");
            }
            return sb.toString().stripTrailing();
        }

        public int size() {
            return entries.size();
        }
    }

    public static final Tool SCRATCHPAD_SET_TOOL = new Tool(
            "scratchpad_set",
            "Save a note to the scratchpad. Use this to track your plan, findings, or decisions.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "key", Map.of("type", "string", "description", "Note key (e.g. 'plan', 'findings')"),
                            "value", Map.of("type", "string", "description", "Note content")
                    ),
                    "required", List.of("key", "value")
            )
    );

    public static final Tool SCRATCHPAD_GET_TOOL = new Tool(
            "scratchpad_get",
            "Read a note from the scratchpad by key.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "key", Map.of("type", "string", "description", "Note key to read")
                    ),
                    "required", List.of("key")
            )
    );

    public static final Tool SCRATCHPAD_LIST_TOOL = new Tool(
            "scratchpad_list",
            "List all scratchpad entries.",
            Map.of("type", "object", "properties", Map.of())
    );

    public static final List<Tool> SCRATCHPAD_TOOLS = List.of(
            SCRATCHPAD_SET_TOOL, SCRATCHPAD_GET_TOOL, SCRATCHPAD_LIST_TOOL
    );

    public static String executeScratchpadTool(Scratchpad scratchpad, String name, Map<String, Object> input) {
        return switch (name) {
            case "scratchpad_set" -> {
                String key = String.valueOf(input.get("key"));
                String value = String.valueOf(input.get("value"));
                scratchpad.set(key, value);
                yield "Saved \"" + key + "\" to scratchpad.";
            }
            case "scratchpad_get" -> {
                String key = String.valueOf(input.get("key"));
                String value = scratchpad.get(key);
                yield value != null ? value : "No entry found for \"" + key + "\".";
            }
            case "scratchpad_list" -> {
                String formatted = scratchpad.format();
                yield formatted.isEmpty() ? "Scratchpad is empty." : formatted;
            }
            default -> "Unknown scratchpad tool: " + name;
        };
    }

    public static List<Message> selectMessages(List<Message> messages, int maxTokens) {
        if (messages.isEmpty()) {
            return List.of();
        }
        if (messages.size() <= 2) {
            return List.copyOf(messages);
        }
        Message first = messages.getFirst();
        int firstTokens = TokenCounter.estimateMessageTokens(first);
        if (firstTokens >= maxTokens) {
            return List.of(first);
        }
        int budget = maxTokens - firstTokens;
        List<Message> tail = new ArrayList<>();
        for (int i = messages.size() - 1; i > 0; ) {
            Message current = messages.get(i);
            if (isToolResultUser(current) && i > 0 && isToolUseAssistant(messages.get(i - 1))) {
                Message assistant = messages.get(i - 1);
                int pairTokens = TokenCounter.estimateMessageTokens(assistant)
                        + TokenCounter.estimateMessageTokens(current);
                if (pairTokens > budget) {
                    break;
                }
                budget -= pairTokens;
                tail.addFirst(current);
                tail.addFirst(assistant);
                i -= 2;
                continue;
            }
            int tokens = TokenCounter.estimateMessageTokens(current);
            if (tokens > budget) {
                break;
            }
            budget -= tokens;
            tail.addFirst(current);
            i--;
        }
        List<Message> result = new ArrayList<>();
        result.add(first);
        result.addAll(tail);
        return result;
    }

    private static boolean isToolResultUser(Message message) {
        return "user".equals(message.role()) && !message.isStringContent();
    }

    private static boolean isToolUseAssistant(Message message) {
        if (!"assistant".equals(message.role()) || message.isStringContent()) {
            return false;
        }
        for (var block : message.contentBlocks()) {
            if (block instanceof ToolUseBlock) {
                return true;
            }
        }
        return false;
    }

    private static final List<Map.Entry<Pattern, String>> POISON_PATTERNS = List.of(
            Map.entry(Pattern.compile("ignore (?:all )?(?:previous |above )?instructions", Pattern.CASE_INSENSITIVE), "instruction override"),
            Map.entry(Pattern.compile("you are now", Pattern.CASE_INSENSITIVE), "role hijacking"),
            Map.entry(Pattern.compile("system:\\s", Pattern.CASE_INSENSITIVE), "system prompt injection"),
            Map.entry(Pattern.compile("\\bdo not\\b.*\\btool", Pattern.CASE_INSENSITIVE), "tool suppression"),
            Map.entry(Pattern.compile("</?(?:system|instruction|prompt)>", Pattern.CASE_INSENSITIVE), "fake XML tags")
    );

    public static List<String> detectContextPoisoning(String text) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<Pattern, String> entry : POISON_PATTERNS) {
            if (entry.getKey().matcher(text).find()) {
                found.add(entry.getValue());
            }
        }
        return found;
    }
}

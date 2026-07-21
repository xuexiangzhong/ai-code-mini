package com.aicode.agent.llm;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultimodalMessageTest {

    @Test
    void openAiProviderFormatsImageBlocks() {
        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                new OpenAICompatibleProvider.Config("key", "https://api.example.com/v1", "gpt-4o")
        );
        Message message = Message.userBlocks(List.of(
                new TextBlock("describe this"),
                new ImageBlock("/tmp/a.png", new byte[] {1, 2, 3}, "image/png")
        ));
        List<Map<String, Object>> formatted = provider.formatMessages(List.of(message), null);
        assertEquals(1, formatted.size());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> content = (List<Map<String, Object>>) formatted.getFirst().get("content");
        assertEquals(2, content.size());
        assertEquals("text", content.get(0).get("type"));
        assertEquals("image_url", content.get(1).get("type"));
    }

    @Test
    void openAiProviderFormatsToolResultsAfterAssistantToolCalls() {
        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                new OpenAICompatibleProvider.Config("key", "https://api.example.com/v1", "gpt-4o")
        );
        Message assistant = Message.assistant(List.of(
                new ToolUseBlock("call_1", "glob", Map.of("pattern", "**/*"))
        ));
        Message toolResults = Message.userBlocks(List.of(
                LLMHelpers.createToolResult("call_1", "found 3 files", false)
        ));

        List<Map<String, Object>> formatted = provider.formatMessages(
                List.of(Message.user("search"), assistant, toolResults),
                null
        );

        assertEquals(3, formatted.size());
        assertEquals("assistant", formatted.get(1).get("role"));
        assertNotNull(formatted.get(1).get("tool_calls"));
        assertEquals("tool", formatted.get(2).get("role"));
        assertEquals("call_1", formatted.get(2).get("tool_call_id"));
        assertEquals("found 3 files", formatted.get(2).get("content"));
    }

    @Test
    void openAiProviderFormatsParallelToolResults() {
        OpenAICompatibleProvider provider = new OpenAICompatibleProvider(
                new OpenAICompatibleProvider.Config("key", "https://api.example.com/v1", "gpt-4o")
        );
        Message assistant = Message.assistant(List.of(
                new ToolUseBlock("call_1", "glob", Map.of()),
                new ToolUseBlock("call_2", "read_file", Map.of())
        ));
        Message toolResults = Message.userBlocks(List.of(
                LLMHelpers.createToolResult("call_1", "a", false),
                LLMHelpers.createToolResult("call_2", "b", false)
        ));

        List<Map<String, Object>> formatted = provider.formatMessages(
                List.of(assistant, toolResults),
                null
        );

        assertEquals(3, formatted.size());
        assertEquals("tool", formatted.get(1).get("role"));
        assertEquals("call_1", formatted.get(1).get("tool_call_id"));
        assertEquals("tool", formatted.get(2).get("role"));
        assertEquals("call_2", formatted.get(2).get("tool_call_id"));
    }
}

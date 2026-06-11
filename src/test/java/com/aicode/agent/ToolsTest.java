package com.aicode.agent;

import com.aicode.agent.llm.LLMHelpers;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolsTest {
    @Nested
    class TestHelperFunctions {
        @Test
        void extractTextJoinsTextBlocks() {
            var content = List.of(
                    (com.aicode.agent.llm.ContentBlock) new TextBlock("Hello "),
                    new ToolUseBlock("1", "test", Map.of()),
                    new TextBlock("world")
            );
            assertEquals("Hello world", LLMHelpers.extractText(content));
        }

        @Test
        void extractTextEmptyForNoTextBlocks() {
            var content = List.of(
                    (com.aicode.agent.llm.ContentBlock) new ToolUseBlock("1", "test", Map.of())
            );
            assertEquals("", LLMHelpers.extractText(content));
        }

        @Test
        void extractToolUses() {
            var content = List.of(
                    (com.aicode.agent.llm.ContentBlock) new TextBlock("Hello"),
                    new ToolUseBlock("1", "read_file", Map.of("path", "a.ts")),
                    new ToolUseBlock("2", "write_file", Map.of("path", "b.ts"))
            );
            var tools = LLMHelpers.extractToolUses(content);
            assertEquals(2, tools.size());
            assertEquals("read_file", tools.get(0).name());
            assertEquals("write_file", tools.get(1).name());
        }

        @Test
        void createToolResult() {
            ToolResultBlock result = LLMHelpers.createToolResult("call_1", "file contents");
            assertEquals(new ToolResultBlock("call_1", "file contents"), result);
        }

        @Test
        void createToolResultWithError() {
            ToolResultBlock result = LLMHelpers.createToolResult("call_1", "not found", true);
            assertEquals(new ToolResultBlock("call_1", "not found", true), result);
        }
    }
}

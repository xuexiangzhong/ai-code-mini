package com.aicode.agent;

import com.aicode.agent.llm.Tool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptFactoryTest {
    private static final List<Tool> TOOLS = List.of(
            new Tool("read_file", "Read a file", Map.of())
    );

    @Test
    void agentPromptIncludesToolStrategyAndNoNarrateRule() {
        String prompt = PromptFactory.buildAgentPrompt(java.nio.file.Path.of("."), TOOLS);
        assertTrue(prompt.contains("## Tool Strategy"));
        assertTrue(prompt.contains("## Environment"));
        assertTrue(prompt.contains("- Workspace:"));
        assertTrue(prompt.contains("parallel"));
        assertTrue(prompt.contains("Minimize scope"));
        assertFalse(prompt.contains("Explain what you are about to do before using tools"));
        assertTrue(prompt.contains("Simple tasks: call tools directly"));
    }

    @Test
    void systemPromptBudgetUsesFifteenPercentWithFloor() {
        assertEquals(8_000, PromptFactory.systemPromptBudget(32_768));
        assertEquals(9_600, PromptFactory.systemPromptBudget(64_000));
        assertEquals(24_000, PromptFactory.systemPromptBudget(200_000));
    }

    @Test
    void chatPromptMentionsAttachments() {
        String prompt = PromptFactory.buildChatPrompt();
        assertTrue(prompt.contains("@ attachments"));
        assertFalse(prompt.contains("cannot access files"));
    }
}

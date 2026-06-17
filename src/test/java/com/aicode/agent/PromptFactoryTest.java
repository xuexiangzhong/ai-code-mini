package com.aicode.agent;

import com.aicode.agent.llm.Tool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
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
    void agentPromptIncludesSkillCatalogWhenPresent(@TempDir Path workspace) throws Exception {
        Path skillDir = workspace.resolve(".cursor/skills/commit");
        Files.createDirectories(skillDir);
        Files.writeString(skillDir.resolve("SKILL.md"), """
                ---
                name: commit
                description: Test skill catalog injection
                ---
                Hidden body
                """);

        String prompt = PromptFactory.buildAgentPrompt(workspace, TOOLS);
        assertTrue(prompt.contains("## Available Skills"));
        assertTrue(prompt.contains("commit"));
        assertTrue(prompt.contains("Test skill catalog injection"));
        assertFalse(prompt.contains("Hidden body"));
    }

    @Test
    void agentPromptIncludesRuleCatalogWhenPresent(@TempDir Path workspace) throws Exception {
        Path rules = workspace.resolve(".aicode/rules");
        Files.createDirectories(rules);
        Files.writeString(rules.resolve("review.mdc"), """
                ---
                description: Review pull requests
                alwaysApply: false
                ---
                Hidden body
                """);

        String prompt = PromptFactory.buildAgentPrompt(workspace, TOOLS);
        assertTrue(prompt.contains("## Available Rules"));
        assertTrue(prompt.contains("Review pull requests"));
        assertFalse(prompt.contains("Hidden body"));
    }

    @Test
    void agentPromptIncludesAlwaysAppliedProjectRules(@TempDir Path workspace) throws Exception {
        Path rules = workspace.resolve(".aicode/rules");
        Files.createDirectories(rules);
        Files.writeString(rules.resolve("style.md"), "- Use 4 spaces");

        String prompt = PromptFactory.buildAgentPrompt(workspace, TOOLS);
        assertTrue(prompt.contains("Always-Applied Project Rules"));
        assertTrue(prompt.contains("4 spaces"));
    }

    @Test
    void chatPromptMentionsAttachments() {
        String prompt = PromptFactory.buildChatPrompt();
        assertTrue(prompt.contains("@ attachments"));
        assertFalse(prompt.contains("cannot access files"));
    }
}

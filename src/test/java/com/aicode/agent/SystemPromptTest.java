package com.aicode.agent;

import com.aicode.agent.llm.Tool;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SystemPromptTest {
    static final List<Tool> SAMPLE_TOOLS = List.of(
            new Tool("read_file", "Read a file", Map.of()),
            new Tool("write_file", "Write a file", Map.of())
    );

    @Nested
    class TestSystemPromptBuilder {
        @Test
        void buildEmpty() {
            assertEquals("", new SystemPromptBuilder().build());
        }

        @Test
        void sectionsSortedByPriority() {
            String prompt = new SystemPromptBuilder()
                    .addSection("Low", "low content", 10)
                    .addSection("High", "high content", 90)
                    .addSection("Mid", "mid content", 50)
                    .build();
            var headings = prompt.lines().filter(l -> l.startsWith("## ")).toList();
            assertEquals(List.of("## High", "## Mid", "## Low"), headings);
        }

        @Test
        void setRoleHighestPriority() {
            String prompt = new SystemPromptBuilder()
                    .addSection("Other", "other", 50)
                    .setRole("You are a helper.")
                    .build();
            assertTrue(prompt.startsWith("## Role"));
            assertTrue(prompt.contains("You are a helper."));
        }

        @Test
        void addRulesAsBulletList() {
            String prompt = new SystemPromptBuilder().addRules(List.of("Rule 1", "Rule 2")).build();
            assertTrue(prompt.contains("- Rule 1"));
            assertTrue(prompt.contains("- Rule 2"));
        }

        @Test
        void addToolGuide() {
            String prompt = new SystemPromptBuilder().addToolGuide(SAMPLE_TOOLS).build();
            assertTrue(prompt.contains("**read_file**: Read a file"));
            assertTrue(prompt.contains("**write_file**: Write a file"));
        }

        @Test
        void setOutputConstraints() {
            String prompt = new SystemPromptBuilder().setOutputConstraints("Be brief.").build();
            assertTrue(prompt.contains("## Output Format"));
            assertTrue(prompt.contains("Be brief."));
        }

        @Test
        void methodChaining() {
            SystemPromptBuilder builder = new SystemPromptBuilder()
                    .setRole("Helper")
                    .addRules(List.of("Rule"))
                    .addToolGuide(SAMPLE_TOOLS)
                    .setOutputConstraints("Format");
            assertEquals(4, builder.getSections().size());
        }

        @Test
        void getSectionsReturnsCopy() {
            SystemPromptBuilder builder = new SystemPromptBuilder().addSection("A", "a", 0);
            var sections = new ArrayList<>(builder.getSections());
            sections.add(new SystemPromptBuilder.PromptSection("B", "b"));
            assertEquals(1, builder.getSections().size());
        }

        @Test
        void clear() {
            SystemPromptBuilder builder = new SystemPromptBuilder().setRole("Test").addRules(List.of("r"));
            builder.clear();
            assertEquals(0, builder.getSections().size());
            assertEquals("", builder.build());
        }

        @Test
        void defaultPriority() {
            SystemPromptBuilder builder = new SystemPromptBuilder()
                    .addSection("A", "a", 0).addSection("B", "b", 0);
            var sections = builder.getSections();
            assertEquals(0, sections.get(0).priority());
            assertEquals(0, sections.get(1).priority());
        }
    }

    @Nested
    class TestBuildWithBudget {
        @Test
        void includeAllUnderBudget() {
            String prompt = new SystemPromptBuilder()
                    .addSection("A", "short", 10)
                    .addSection("B", "short", 20)
                    .buildWithBudget(10000);
            assertTrue(prompt.contains("## A"));
            assertTrue(prompt.contains("## B"));
        }

        @Test
        void dropLowPriorityOverBudget() {
            String prompt = new SystemPromptBuilder()
                    .addSection("Important", "x".repeat(50), 100)
                    .addSection("Nice", "y".repeat(50), 50)
                    .addSection("Optional", "z".repeat(50), 10)
                    .buildWithBudget(130);
            assertTrue(prompt.contains("## Important"));
            assertTrue(prompt.contains("## Nice"));
            assertFalse(prompt.contains("## Optional"));
        }

        @Test
        void alwaysIncludeFirstSection() {
            String prompt = new SystemPromptBuilder()
                    .addSection("Big", "x".repeat(1000), 100)
                    .buildWithBudget(10);
            assertTrue(prompt.contains("## Big"));
        }

        @Test
        void emptyBuilder() {
            assertEquals("", new SystemPromptBuilder().buildWithBudget(100));
        }
    }

    @Nested
    class TestCreateCodingAssistantPrompt {
        @Test
        void allSectionsPresent() {
            String prompt = SystemPromptBuilder.createCodingAssistantPrompt(SAMPLE_TOOLS);
            assertTrue(prompt.contains("## Role"));
            assertTrue(prompt.contains("## Rules"));
            assertTrue(prompt.contains("## Available Tools"));
            assertTrue(prompt.contains("## Output Format"));
            assertTrue(prompt.contains("read_file"));
        }

        @Test
        void roleFirst() {
            assertTrue(SystemPromptBuilder.createCodingAssistantPrompt(SAMPLE_TOOLS).startsWith("## Role"));
        }

        @Test
        void emptyTools() {
            String prompt = SystemPromptBuilder.createCodingAssistantPrompt(List.of());
            assertTrue(prompt.contains("## Role"));
            assertTrue(prompt.contains("## Available Tools"));
        }
    }
}

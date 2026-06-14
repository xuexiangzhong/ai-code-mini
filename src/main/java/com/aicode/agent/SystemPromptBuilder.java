package com.aicode.agent;

import com.aicode.agent.llm.Tool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Build structured system prompts from composable sections.
 */
public class SystemPromptBuilder {
    public record PromptSection(String title, String content, int priority) {
        public PromptSection(String title, String content) {
            this(title, content, 0);
        }
    }

    private final List<PromptSection> sections = new ArrayList<>();

    public SystemPromptBuilder addSection(String title, String content, int priority) {
        sections.add(new PromptSection(title, content, priority));
        return this;
    }

    public SystemPromptBuilder setRole(String role) {
        return addSection("Role", role, 100);
    }

    public SystemPromptBuilder addRules(List<String> rules) {
        String content = String.join("\n", rules.stream().map(r -> "- " + r).toList());
        return addSection("Rules", content, 80);
    }

    public SystemPromptBuilder addToolGuide(List<Tool> tools) {
        String content = String.join("\n", tools.stream()
                .map(t -> "- **" + t.name() + "**: " + t.description())
                .toList());
        return addSection("Available Tools", content, 60);
    }

    public SystemPromptBuilder setOutputConstraints(String constraints) {
        return addSection("Output Format", constraints, 40);
    }

    public String build() {
        return sections.stream()
                .sorted(Comparator.comparingInt(PromptSection::priority).reversed())
                .map(s -> "## " + s.title() + "\n" + s.content())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    public String buildWithBudget(int maxChars) {
        List<PromptSection> sorted = sections.stream()
                .sorted(Comparator.comparingInt(PromptSection::priority).reversed())
                .toList();
        List<String> parts = new ArrayList<>();
        int total = 0;
        for (PromptSection section : sorted) {
            String block = "## " + section.title() + "\n" + section.content();
            if (total + block.length() + 2 > maxChars && !parts.isEmpty()) {
                break;
            }
            parts.add(block);
            total += block.length() + 2;
        }
        return String.join("\n\n", parts);
    }

    public List<PromptSection> getSections() {
        return List.copyOf(sections);
    }

    public SystemPromptBuilder clear() {
        sections.clear();
        return this;
    }

    public static String createCodingAssistantPrompt(List<Tool> tools) {
        return PromptFactory.buildAgentPrompt(Path.of(System.getProperty("user.dir")), tools);
    }
}

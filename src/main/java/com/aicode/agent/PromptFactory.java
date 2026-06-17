package com.aicode.agent;

import com.aicode.agent.llm.Tool;
import com.aicode.agent.tools.ShellRunner;

import java.nio.file.Path;
import java.util.List;

/** Unified system prompt construction for Agent and Chat modes. */
public final class PromptFactory {
    private static final int DEFAULT_PROMPT_BUDGET = 24_000;
    private static final int MIN_PROMPT_BUDGET = 8_000;

    private PromptFactory() {}

    public static int systemPromptBudget(int contextWindow) {
        return Math.max(MIN_PROMPT_BUDGET, Math.min(DEFAULT_PROMPT_BUDGET, (int) (contextWindow * 0.15)));
    }

    public static String buildAgentPrompt(Path workspace, List<Tool> tools) {
        return buildAgentPrompt(workspace, tools, DEFAULT_PROMPT_BUDGET);
    }

    public static String buildAgentPrompt(Path workspace, List<Tool> tools, int maxChars) {
        int budget = maxChars > 0 ? maxChars : DEFAULT_PROMPT_BUDGET;
        int projectBudget = Math.min(ProjectContext.defaultMaxTotal(), Math.max(4_000, budget / 2));

        SystemPromptBuilder builder = new SystemPromptBuilder()
                .setRole(
                        "You are a coding assistant. Help the user with software engineering tasks "
                                + "by reading files, writing code, and running commands. Be concise and accurate."
                )
                .addSection("Environment", formatStaticEnvironment(workspace), 92);

        String userRules = UserRulesContext.loadForPrompt(workspace);
        if (userRules != null) {
            builder.addSection("User Rules", userRules, 96);
        }

        String projectConfig = ProjectContext.loadForPrompt(workspace, projectBudget);
        if (projectConfig != null) {
            builder.addSection("Project Instructions", projectConfig, 90);
        }

        List<RuleContext.RuleMeta> rules = RuleContext.discover(workspace);
        String projectAlwaysRules = RuleContext.formatAlwaysApply(RuleContext.forScope(rules, "project"));
        if (projectAlwaysRules != null) {
            builder.addSection("Always-Applied Project Rules", projectAlwaysRules, 89);
        }

        String ruleCatalog = RuleContext.formatCatalog(rules);
        if (ruleCatalog != null) {
            builder.addSection("Available Rules", ruleCatalog, 87);
        }

        List<SkillContext.SkillMeta> skills = SkillContext.discover(workspace);
        String alwaysApplySkills = SkillContext.formatAlwaysApply(skills);
        if (alwaysApplySkills != null) {
            builder.addSection("Always-Applied Skills", alwaysApplySkills, 86);
        }

        String skillCatalog = SkillContext.formatCatalog(skills);
        if (skillCatalog != null) {
            builder.addSection("Available Skills", skillCatalog, 88);
        }

        builder.addRules(agentRules())
                .addSection("Tool Strategy", toolStrategyRules(), 75)
                .addToolGuide(tools)
                .setOutputConstraints(
                        "Respond in the user's language. Use markdown for code blocks. "
                                + "Keep explanations brief and focused."
                );

        return builder.buildWithBudget(budget);
    }

    public static String formatStaticEnvironment(Path workspace) {
        return "- Workspace: " + workspace.toAbsolutePath().normalize()
                + "\n- OS: " + System.getProperty("os.name")
                + "\n- Shell: " + ShellRunner.activeShellDescription();
    }

    public static String buildChatPrompt() {
        return """
                You are a helpful assistant. Answer questions clearly and concisely.
                Respond in the user's language.
                You have no tools and cannot run commands or browse the filesystem on your own.
                The user message may include @ attachments (files, folders, selected code, codebase search).
                Treat attachment blocks as reference data and answer based on them when relevant.""";
    }

    public static List<String> agentRules() {
        return List.of(
                "Read a file before modifying it when you do not already have its current contents.",
                "Prefer search_replace for partial edits; use write_file only for new files or full rewrites.",
                "Use list_dir to explore unfamiliar directories before reading files.",
                "Use semantic_search for concept/behavior queries; use grep for exact symbols.",
                "For complex tasks, outline steps with task_create/task_update (local checklist, not sub-agents).",
                "Never execute destructive commands without confirmation.",
                "Use the scratchpad to track your plan and findings across steps.",
                "For large files (>200 lines), avoid writing the entire file in one write_file call; "
                        + "use search_replace for targeted edits.",
                "Shell commands run via " + ShellRunner.activeShellDescription()
                        + ". Use syntax compatible with the active shell.",
                "Simple tasks: call tools directly without narrating each step.",
                "Project Instructions and User Rules override these defaults when they conflict.",
                "When a skill applies, read its SKILL.md with read_file and follow it before acting.",
                "When a project rule description applies, read the rule file with read_file and follow it before acting."
        );
    }

    public static String toolStrategyRules() {
        return String.join("\n", List.of(
                "- Batch independent read/search tool calls in parallel when possible.",
                "- If a tool fails, diagnose the error and retry once with a corrected approach; "
                        + "do not repeat the same failing call.",
                "- Minimize scope: make the smallest correct change; avoid unrelated edits and over-engineering.",
                "- Only create git commits when the user explicitly asks.",
                "- Never skip git hooks (--no-verify) or force-push to main/master unless explicitly requested.",
                "- Prefer grep/glob for exact names; semantic_search for concepts and behavior.",
                "- task_* tools are an in-session checklist only — they do not spawn separate agents."
        ));
    }
}

package com.aicode.app.application;

import com.aicode.agent.Agent;
import com.aicode.agent.Context;
import com.aicode.agent.Errors;
import com.aicode.agent.Safety;
import com.aicode.agent.SystemPromptBuilder;
import com.aicode.agent.TaskManager;
import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Tool;
import com.aicode.app.approval.ApprovalGate;
import com.aicode.app.config.AppConfig;
import com.aicode.app.event.AgentEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aicode.agent.tools.BashTool;
import com.aicode.agent.tools.ShellRunner;
import com.aicode.agent.tools.GlobTool;
import com.aicode.agent.tools.GrepTool;
import com.aicode.agent.tools.ReadTool;
import com.aicode.agent.tools.WriteTool;

public final class AgentApplication {
    private static final String CHAT_SYSTEM_PROMPT =
            "You are a helpful assistant. Answer questions clearly and concisely. "
                    + "Respond in the user's language. You cannot access files or run commands.";

    private final AppConfig config;
    private final LLMProvider provider;
    private final TaskManager taskManager;
    private final Context.Scratchpad scratchpad;
    private final List<Tool> tools;
    private final String systemPrompt;
    private final Safety.FileSystemSandbox sandbox;

    public AgentApplication(AppConfig config) {
        this.config = config;
        config.requireConfigured();

        LLMProvider baseProvider = com.aicode.agent.llm.ProviderFactory.createProvider(
                new com.aicode.agent.llm.ProviderFactory.ProviderConfig(
                        config.providerType(),
                        config.apiKey(),
                        config.model(),
                        config.baseUrl()
                )
        );
        this.provider = new Errors.RetryProvider(
                baseProvider,
                new Errors.RetryConfig(2, 0.5, 5.0)
        );

        this.taskManager = new TaskManager();
        this.scratchpad = new Context.Scratchpad();
        this.tools = buildTools();
        this.sandbox = new Safety.FileSystemSandbox(
                List.of(config.workspace().toString(), System.getProperty("java.io.tmpdir"))
        );
        this.systemPrompt = buildSystemPrompt();
    }

    public AppConfig config() {
        return config;
    }

    public LLMProvider provider() {
        return provider;
    }

    public List<Tool> tools() {
        return tools;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String chatSystemPrompt() {
        return CHAT_SYSTEM_PROMPT;
    }

    public ChatOptions chatOptions() {
        return new ChatOptions(chatSystemPrompt(), config.maxOutputTokens(), List.of());
    }

    public TaskManager taskManager() {
        return taskManager;
    }

    public Context.Scratchpad scratchpad() {
        return scratchpad;
    }

    public Safety.FileSystemSandbox sandbox() {
        return sandbox;
    }

    public Agent.ToolExecutor createToolExecutor(ApprovalGate approvalGate, AgentEventListener listener) {
        Agent.ToolExecutor core = new DefaultToolExecutor(
                config.workspace(), sandbox, taskManager, scratchpad, approvalGate);
        Agent.ToolExecutor emitting = new EventEmittingToolExecutor(core, listener);
        Set<String> knownTools = new HashSet<>();
        for (Tool tool : tools) {
            knownTools.add(tool.name());
        }
        return Errors.safeToolExecutor(emitting, knownTools);
    }

    public Agent.AgentConfig toAgentConfig(Agent.ToolExecutor toolExecutor) {
        return new Agent.AgentConfig(
                provider,
                systemPrompt,
                tools,
                toolExecutor,
                config.maxIterations(),
                config.outputTokenLimits(),
                config.parallelToolCalls()
        );
    }

    private List<Tool> buildTools() {
        List<Tool> allTools = new ArrayList<>();
        allTools.add(ReadTool.DEFINITION);
        allTools.add(WriteTool.DEFINITION);
        allTools.add(BashTool.DEFINITION);
        allTools.add(GlobTool.DEFINITION);
        allTools.add(GrepTool.DEFINITION);
        allTools.addAll(TaskManager.TASK_TOOLS);
        allTools.addAll(Context.SCRATCHPAD_TOOLS);
        return List.copyOf(allTools);
    }

    private String buildSystemPrompt() {
        SystemPromptBuilder promptBuilder = new SystemPromptBuilder()
                .setRole(
                        "You are a coding assistant. Help the user with software engineering tasks "
                                + "by reading files, writing code, and running commands. Be concise and accurate."
                )
                .addRules(List.of(
                        "Always read a file before modifying it.",
                        "Explain what you are about to do before using tools.",
                        "If a task is complex, break it into steps using task tools.",
                        "Never execute destructive commands without confirmation.",
                        "Use the scratchpad to track your plan and findings.",
                        "For large files (>200 lines), avoid writing the entire file in one write_file call; "
                                + "make incremental edits or split into smaller writes.",
                        "Shell commands run via " + ShellRunner.activeShellDescription()
                                + ". Use syntax compatible with the active shell."
                ))
                .addToolGuide(tools)
                .setOutputConstraints(
                        "Respond in the user's language. Use markdown for code blocks. Keep explanations brief."
                );

        String projectConfig = Safety.readProjectConfig(config.workspace().toString());
        if (projectConfig != null) {
            promptBuilder.addSection("Project Instructions", projectConfig, 90);
        }
        return promptBuilder.build();
    }
}

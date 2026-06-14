package com.aicode.app.application;

import com.aicode.agent.Agent;
import com.aicode.agent.Context;
import com.aicode.agent.Errors;
import com.aicode.agent.PromptFactory;
import com.aicode.agent.Safety;
import com.aicode.agent.TaskManager;
import com.aicode.agent.index.CodebaseIndexManager;
import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.EmbeddingProvider;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.OpenAICompatibleEmbeddingProvider;
import com.aicode.agent.llm.Tool;
import com.aicode.app.event.AgentEvent;
import com.aicode.app.session.FileEditProposal;
import com.aicode.app.approval.ApprovalGate;
import com.aicode.app.config.AppConfig;
import com.aicode.app.event.AgentEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.aicode.agent.tools.BashTool;
import com.aicode.agent.tools.DeleteTool;
import com.aicode.agent.tools.GlobTool;
import com.aicode.agent.tools.GrepTool;
import com.aicode.agent.tools.ListDirTool;
import com.aicode.agent.tools.ReadTool;
import com.aicode.agent.tools.SearchReplaceTool;
import com.aicode.agent.tools.SemanticSearchTool;
import com.aicode.agent.tools.WriteTool;

public final class AgentApplication {
    private final AppConfig config;
    private final LLMProvider provider;
    private final TaskManager taskManager;
    private final Context.Scratchpad scratchpad;
    private final List<Tool> tools;
    private final String systemPrompt;
    private final String chatSystemPrompt;
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
        int promptBudget = PromptFactory.systemPromptBudget(config.contextWindow());
        this.systemPrompt = PromptFactory.buildAgentPrompt(config.workspace(), tools, promptBudget);
        this.chatSystemPrompt = PromptFactory.buildChatPrompt();
        warmCodebaseIndex();
    }

    public EmbeddingProvider embeddingProvider() {
        if (config.apiKey() == null || config.apiKey().isBlank()) {
            return null;
        }
        String model = System.getenv().getOrDefault("EMBEDDING_MODEL", "text-embedding-3-small");
        return new OpenAICompatibleEmbeddingProvider(
                new OpenAICompatibleEmbeddingProvider.Config(config.apiKey(), config.baseUrl(), model)
        );
    }

    public void warmCodebaseIndex() {
        String model = System.getenv().getOrDefault("EMBEDDING_MODEL", "text-embedding-3-small");
        CodebaseIndexManager.warmIndex(
                config.workspace(),
                config.apiKey(),
                config.baseUrl(),
                model
        );
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
        return chatSystemPrompt;
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
        java.util.function.Consumer<FileEditProposal> onFileEdit = proposal -> {
            if (listener != null) {
                listener.onEvent(new AgentEvent.FileEditProposed(
                        proposal.id(),
                        proposal.filePath().toString(),
                        proposal.oldContent(),
                        proposal.newContent(),
                        proposal.created(),
                        proposal.diffText()
                ));
            }
        };
        Agent.ToolExecutor core = new DefaultToolExecutor(
                config.workspace(),
                sandbox,
                taskManager,
                scratchpad,
                approvalGate,
                onFileEdit,
                embeddingProvider()
        );
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
        allTools.add(SearchReplaceTool.DEFINITION);
        allTools.add(DeleteTool.DEFINITION);
        allTools.add(BashTool.DEFINITION);
        allTools.add(GlobTool.DEFINITION);
        allTools.add(GrepTool.DEFINITION);
        allTools.add(ListDirTool.DEFINITION);
        allTools.add(SemanticSearchTool.DEFINITION);
        allTools.addAll(TaskManager.TASK_TOOLS);
        allTools.addAll(Context.SCRATCHPAD_TOOLS);
        return List.copyOf(allTools);
    }
}

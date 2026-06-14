package com.aicode.agent;

import com.aicode.agent.llm.OutputTokenLimits;
import com.aicode.agent.llm.ProviderFactory;
import com.aicode.agent.llm.Tool;
import com.aicode.agent.PromptFactory;
import com.aicode.agent.tools.BashTool;
import com.aicode.agent.tools.DeleteTool;
import com.aicode.agent.tools.GlobTool;
import com.aicode.agent.tools.GrepTool;
import com.aicode.agent.tools.ListDirTool;
import com.aicode.agent.tools.ReadTool;
import com.aicode.agent.tools.SearchReplaceTool;
import com.aicode.agent.tools.SemanticSearchTool;
import com.aicode.agent.tools.WriteTool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * CLI entry point — ties together all chapters into a working agent.
 *
 * Configurable via environment variables:
 *   AGENT_NAME   — display name shown in banner and prompt (default: "AI Coding")
 *   AGENT_ICON   — emoji icon for banner and prompt (default: "🤖")
 *   DEEPSEEK_API_KEY / OPENAI_API_KEY — LLM API key (required)
 *   LLM_BASE_URL — API endpoint URL (default: https://api.deepseek.com/v1/chat/completions)
 *   LLM_MODEL    — model name (default: deepseek-chat)
 */
public final class Main {
    private static final int BOX_WIDTH = 58;
    private static final String ROSE = "\033[38;5;204m";
    private static final String ROSE_BOLD = "\033[1;38;5;204m";
    private static final String GRAY = "\033[38;5;247m";
    private static final String PINK = "\033[38;5;218m";
    private static final String RESET = "\033[0m";

    private Main() {}

    public static void main(String[] args) throws Exception {
        String agentName = env("AGENT_NAME", "AI Coding");
        String agentIcon = env("AGENT_ICON", "🤖");
        String apiKey = env("DEEPSEEK_API_KEY", env("OPENAI_API_KEY", ""));
        String baseUrl = env("LLM_BASE_URL", "https://api.deepseek.com/v1/chat/completions");
        String model = env("LLM_MODEL", "deepseek-chat");
        String projectDir = System.getProperty("user.dir");

        if (apiKey.isBlank()) {
            System.err.println("Error: Set DEEPSEEK_API_KEY or OPENAI_API_KEY environment variable.");
            System.exit(1);
        }

        var baseProvider = ProviderFactory.createProvider(
                new ProviderFactory.ProviderConfig("openai-compatible", apiKey, model, baseUrl)
        );
        var provider = new Errors.RetryProvider(
                baseProvider,
                new Errors.RetryConfig(2, 0.5, 5.0)
        );

        TaskManager taskManager = new TaskManager();
        Context.Scratchpad scratchpad = new Context.Scratchpad();

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

        String tempDir = System.getProperty("java.io.tmpdir");
        Safety.FileSystemSandbox sandbox = new Safety.FileSystemSandbox(
                List.of(projectDir, tempDir)
        );

        Agent.ToolExecutor rawExecutor = (name, input) -> {
            if (name.startsWith("task_")) {
                return CompletableFuture.completedFuture(
                        TaskManager.executeTaskTool(taskManager, name, input)
                );
            }
            if (name.startsWith("scratchpad_")) {
                return CompletableFuture.completedFuture(
                        Context.executeScratchpadTool(scratchpad, name, input)
                );
            }

            if ("read_file".equals(name) || "write_file".equals(name) || "search_replace".equals(name)
                    || "delete_file".equals(name)) {
                String filePath = String.valueOf(input.getOrDefault("file_path", ""));
                String blocked = sandbox.check(filePath);
                if (blocked != null) {
                    return CompletableFuture.completedFuture(blocked);
                }
            }

            if ("glob".equals(name) || "grep".equals(name) || "list_dir".equals(name)) {
                String path = String.valueOf(input.getOrDefault("path", "."));
                String blocked = sandbox.check(path);
                if (blocked != null) {
                    return CompletableFuture.completedFuture(blocked);
                }
            }

            if ("bash".equals(name)) {
                String command = String.valueOf(input.getOrDefault("command", ""));
                String danger = Safety.checkDangerousCommand(command);
                if (danger != null) {
                    return CompletableFuture.completedFuture(
                            "⚠️ Blocked: " + danger + ". This command requires user confirmation."
                    );
                }
            }

            ToolDisplay.Spinner spinner = new ToolDisplay.Spinner(name + "...");
            spinner.start();
            long start = System.nanoTime();

            try {
                String result = switch (name) {
                    case "read_file" -> ReadTool.execute(ReadTool.Input.fromMap(input));
                    case "write_file" -> WriteTool.execute(WriteTool.Input.fromMap(input));
                    case "search_replace" -> SearchReplaceTool.execute(SearchReplaceTool.Input.fromMap(input));
                    case "delete_file" -> DeleteTool.execute(DeleteTool.Input.fromMap(input));
                    case "bash" -> BashTool.execute(BashTool.Input.fromMap(input)).join();
                    case "glob" -> GlobTool.execute(GlobTool.Input.fromMap(input));
                    case "grep" -> GrepTool.execute(GrepTool.Input.fromMap(input));
                    case "list_dir" -> ListDirTool.execute(ListDirTool.Input.fromMap(input));
                    case "semantic_search" -> SemanticSearchTool.execute(
                            SemanticSearchTool.Input.fromMap(input), Path.of(projectDir));
                    default -> "Error: unknown tool \"" + name + "\"";
                };
                double ms = (System.nanoTime() - start) / 1_000_000.0;
                spinner.succeed(name + " [" + Math.round(ms) + "ms]");
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                spinner.fail(name + " failed");
                return CompletableFuture.failedFuture(e);
            }
        };

        Set<String> knownTools = new HashSet<>();
        for (Tool t : allTools) {
            knownTools.add(t.name());
        }
        Agent.ToolExecutor executeTool = Errors.safeToolExecutor(rawExecutor, knownTools);

        String systemPrompt = PromptFactory.buildAgentPrompt(Path.of(projectDir), allTools);

        showBanner(agentName, agentIcon, model, projectDir);

        String promptStr = ROSE + agentIcon + " > " + RESET;
        Repl.runRepl(new Repl.ReplConfig(
                promptStr,
                List.of(
                        new Repl.Command("/tasks", "Show current tasks",
                                () -> {
                                    String s = taskManager.formatForLLM();
                                    return "(no tasks)".equals(s) ? "No tasks." : s;
                                }),
                        new Repl.Command("/notes", "Show scratchpad", () -> {
                            String s = scratchpad.format();
                            return s.isEmpty() ? "Scratchpad is empty." : s;
                        }),
                        new Repl.Command("/reset", "Clear tasks and scratchpad", () -> {
                            taskManager.clear();
                            scratchpad.clear();
                            return "Cleared.";
                        })
                ),
                text -> {
                    Agent.AgentConfig config = new Agent.AgentConfig(
                            provider,
                            systemPrompt,
                            allTools,
                            executeTool,
                            50,
                            OutputTokenLimits.defaults(),
                            true
                    );
                    return Agent.runAgent(config, text).thenApply(r -> Markdown.renderMarkdown(r.text()));
                }
        ));
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null ? value : defaultValue;
    }

    private static int displayWidth(String s) {
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp > 0xFFFF) {
                w += 2;
            } else if (Character.getType(cp) == Character.OTHER_LETTER
                    && cp >= 0x4E00 && cp <= 0x9FFF) {
                w += 2;
            } else {
                w += 1;
            }
            i += Character.charCount(cp);
        }
        return w;
    }

    private static String row(String text, String... colorParts) {
        int pad = BOX_WIDTH - displayWidth(text);
        String inner = String.join("", colorParts) + " ".repeat(Math.max(0, pad)) + ROSE;
        return "║" + inner + "║";
    }

    private static void showBanner(String name, String icon, String model, String projectDir) {
        String cwd = projectDir;
        if (cwd.length() > 40) {
            cwd = "..." + cwd.substring(cwd.length() - 37);
        }

        String title = "     " + icon + "  " + name;
        String subtitle = "     Your AI Coding Assistant";
        String border = "═".repeat(BOX_WIDTH);
        String blank = row("");

        System.out.printf("""
                %s╔%s╗
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s
                %s╚%s╝%s
                """,
                ROSE, border,
                blank,
                row(title, "     " + icon + "  ", ROSE_BOLD, name, RESET, ROSE),
                blank,
                row(subtitle, GRAY, subtitle),
                blank,
                row("     Model:  " + model, GRAY, "     Model:  ", PINK, model),
                row("     Dir:    " + cwd, GRAY, "     Dir:    ", PINK, cwd),
                blank,
                row("     Type /help for commands · /exit to quit",
                        GRAY, "     Type ", PINK, "/help", GRAY, " for commands · ", PINK, "/exit", GRAY, " to quit"),
                blank,
                ROSE, border, RESET
        );
    }
}

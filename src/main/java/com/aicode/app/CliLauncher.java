package com.aicode.app;

import com.aicode.agent.Repl;
import com.aicode.app.application.AgentApplication;
import com.aicode.app.config.AppConfig;
import com.aicode.app.config.AppConfigStore;
import com.aicode.app.event.TerminalEventListener;
import com.aicode.app.session.AgentSessionService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * CLI/REPL launcher using the shared AgentApplication wiring.
 */
public final class CliLauncher {
    private static final int BOX_WIDTH = 58;
    private static final String ROSE = "\033[38;5;204m";
    private static final String ROSE_BOLD = "\033[1;38;5;204m";
    private static final String GRAY = "\033[38;5;247m";
    private static final String PINK = "\033[38;5;218m";
    private static final String RESET = "\033[0m";

    private CliLauncher() {}

    public static void run(String[] args) throws Exception {
        AppConfig config = AppConfigStore.load();

        AgentApplication application = null;
        AgentSessionService sessions = null;
        TerminalEventListener listener = new TerminalEventListener();
        if (config.isConfigured()) {
            application = new AgentApplication(config);
            sessions = new AgentSessionService(application, true);
            sessions.createSession(config.workspace());
        }

        showBanner(config);

        AgentApplication appRef = application;
        AgentSessionService sessionsRef = sessions;
        String promptStr = ROSE + config.agentIcon() + " > " + RESET;
        Repl.runRepl(new Repl.ReplConfig(
                promptStr,
                List.of(
                        new Repl.Command("/tasks", "Show current tasks",
                                () -> {
                                    if (appRef == null) {
                                        return configHint();
                                    }
                                    String s = appRef.taskManager().formatForLLM();
                                    return "(no tasks)".equals(s) ? "No tasks." : s;
                                }),
                        new Repl.Command("/notes", "Show scratchpad", () -> {
                            if (appRef == null) {
                                return configHint();
                            }
                            String s = appRef.scratchpad().format();
                            return s.isEmpty() ? "Scratchpad is empty." : s;
                        }),
                        new Repl.Command("/reset", "Clear tasks and scratchpad", () -> {
                            if (sessionsRef == null) {
                                return configHint();
                            }
                            sessionsRef.resetSession(sessionsRef.getOrCreateDefaultSession().sessionId());
                            return "Cleared.";
                        })
                ),
                text -> {
                    if (!config.isConfigured()) {
                        return CompletableFuture.completedFuture(configHint());
                    }
                    if (sessionsRef == null) {
                        return CompletableFuture.completedFuture("Agent 未初始化。");
                    }
                    return sessionsRef.sendMessageForCli(text, listener);
                }
        ));
    }

    private static String configHint() {
        return """
                API Key 未配置。
                请编辑当前目录下的 aicode.yaml 并设置 apiKey，或使用 JavaFX 界面配置后保存。
                配置文件: %s
                """.formatted(AppConfigStore.configPath()).strip();
    }

    private static void showBanner(AppConfig config) {
        String cwd = config.workspace().toString();
        if (cwd.length() > 40) {
            cwd = "..." + cwd.substring(cwd.length() - 37);
        }

        String name = config.agentName();
        String icon = config.agentIcon();
        String model = config.model();
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
                row(config.isConfigured()
                                ? "     Config: aicode.yaml loaded"
                                : "     Config: missing apiKey in aicode.yaml",
                        GRAY,
                        config.isConfigured() ? "     Config: " : "     Config: ",
                        config.isConfigured() ? PINK : ROSE,
                        config.isConfigured() ? "ready" : "required"),
                blank,
                row("     Type /help for commands · /exit to quit",
                        GRAY, "     Type ", PINK, "/help", GRAY, " for commands · ", PINK, "/exit", GRAY, " to quit"),
                blank,
                ROSE, border, RESET
        );
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
}

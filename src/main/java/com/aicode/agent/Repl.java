package com.aicode.agent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Interactive REPL with built-in commands.
 * Mirrors Python {@code repl.py} and TypeScript {@code repl.ts} structure.
 */
public final class Repl {
    private static final String DEFAULT_PROMPT = "> ";
    private static final List<String> DEFAULT_EXIT_KEYWORDS = List.of("/exit", "/quit");

    public record Command(String name, String description, Supplier<String> execute) {}

    public record ReplConfig(
            String prompt,
            List<String> exitKeywords,
            List<Command> commands,
            InputHandler onInput
    ) {
        public ReplConfig(String prompt, List<Command> commands, InputHandler onInput) {
            this(prompt, DEFAULT_EXIT_KEYWORDS, commands, onInput);
        }

        public ReplConfig(List<Command> commands, InputHandler onInput) {
            this(DEFAULT_PROMPT, DEFAULT_EXIT_KEYWORDS, commands, onInput);
        }
    }

    @FunctionalInterface
    public interface InputHandler {
        CompletableFuture<String> handle(String input);
    }

    /**
     * Handle returned from {@link #createRepl} (mirrors TypeScript {@code createRepl}).
     */
    public record ReplHandle(
            ReplConfig config,
            List<Command> allCommands,
            InputHandler onInput
    ) {
        public CompletableFuture<String> processInput(String raw) {
            String text = normalizeInput(raw);
            if (text.isEmpty()) {
                return CompletableFuture.completedFuture("");
            }

            String cmdName = parseCommand(text);
            if (config.exitKeywords().contains(cmdName)) {
                return CompletableFuture.completedFuture(null);
            }

            for (Command cmd : allCommands) {
                if (cmd.name().equals(cmdName)) {
                    String result = cmd.execute().get();
                    return CompletableFuture.completedFuture(result != null ? result : "");
                }
            }

            if (onInput != null) {
                return onInput.handle(text);
            }

            return CompletableFuture.completedFuture(
                    "Unknown command: " + cmdName + ". Type /help for available commands."
            );
        }

        public void close() {
            // no-op, mirrors TypeScript createRepl.close()
        }
    }

    private Repl() {}

    public static ReplHandle createRepl() {
        return createRepl(new ReplConfig("> ", List.of(), null));
    }

    public static ReplHandle createRepl(ReplConfig config) {
        List<Command> allCommands = new ArrayList<>();
        allCommands.add(new Command("/help", "Show available commands", () -> "help_placeholder"));
        allCommands.add(new Command("/clear", "Clear the screen", () -> {
            System.out.print("\033[2J\033[H");
            return null;
        }));
        if (config.commands() != null) {
            allCommands.addAll(config.commands());
        }

        for (Command cmd : allCommands) {
            if ("/help".equals(cmd.name())) {
                allCommands.set(allCommands.indexOf(cmd), new Command(
                        "/help",
                        "Show available commands",
                        () -> formatHelp(allCommands, config.exitKeywords())
                ));
                break;
            }
        }

        return new ReplHandle(config, allCommands, config.onInput());
    }

    public static String formatHelp(List<Command> commands, List<String> exitKeywords) {
        StringBuilder sb = new StringBuilder("Available commands:\n");
        for (Command cmd : commands) {
            sb.append(String.format("  %-12s %s%n", cmd.name(), cmd.description()));
        }
        sb.append(String.format("  %-12s Exit the REPL%n", exitKeywords.getFirst()));
        return sb.toString().stripTrailing();
    }

    public static boolean isMultiLine(String text) {
        return text.contains("\n");
    }

    public static String normalizeInput(String text) {
        return text.strip();
    }

    public static String parseCommand(String text) {
        String stripped = text.strip();
        if (stripped.isEmpty()) {
            return "";
        }
        return stripped.split("\\s+")[0].toLowerCase();
    }

    /**
     * Run the REPL interactively (mirrors TypeScript {@code runRepl}).
     */
    public static void runRepl(ReplConfig config) throws IOException {
        ReplHandle repl = createRepl(config);
        String prompt = config.prompt() != null ? config.prompt() : DEFAULT_PROMPT;
        boolean useCbreak = TermReader.isTTY();

        while (true) {
            String raw;
            if (useCbreak) {
                raw = TermReader.readLine(prompt);
                if (raw == null) {
                    System.out.println("Goodbye!");
                    break;
                }
            } else {
                System.out.print(prompt);
                System.out.flush();
                raw = System.console() != null
                        ? System.console().readLine()
                        : new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
                if (raw == null) {
                    break;
                }
            }

            String result = repl.processInput(raw).join();
            if (result == null) {
                System.out.println("Goodbye!");
                break;
            }
            if (!result.isEmpty()) {
                System.out.println(result);
            }
        }
    }
}

package com.aicode.agent.tools;

import com.aicode.agent.llm.Tool;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class BashTool {
    public static final Tool DEFINITION = new Tool(
            "bash",
            "Execute a shell command and return its output. "
                    + "On Linux/macOS uses bash; on Windows uses Git Bash when available, otherwise PowerShell. "
                    + "Use this to run shell commands, scripts, or system utilities.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "command", Map.of(
                                    "type", "string",
                                    "description", "The shell command to execute"
                            ),
                            "timeout", Map.of(
                                    "type", "number",
                                    "description", "Timeout in seconds (default: 30)"
                            )
                    ),
                    "required", List.of("command")
            )
    );

    public static final int MAX_OUTPUT_SIZE = 100_000;

    public record Input(String command, double timeout) {
        public static Input fromMap(Map<String, Object> map) {
            String command = String.valueOf(map.get("command"));
            double timeout = map.containsKey("timeout") ? ((Number) map.get("timeout")).doubleValue() : 30.0;
            return new Input(command, timeout);
        }
    }

    private BashTool() {}

    public static CompletableFuture<String> execute(Input input) {
        return execute(input, null);
    }

    public static CompletableFuture<String> execute(Input input, Path workingDirectory) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ShellRunner.ShellInvocation shell = ShellRunner.resolve(input.command());
                ProcessBuilder pb = new ProcessBuilder(shell.command());
                if (workingDirectory != null) {
                    pb.directory(workingDirectory.toFile());
                }
                pb.redirectErrorStream(false);
                Process process = pb.start();

                boolean finished = process.waitFor((long) (input.timeout() * 1000), TimeUnit.MILLISECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return "Error: command timed out after " + input.timeout() + "s";
                }

                String stdout = readStream(process.getInputStream());
                String stderr = readStream(process.getErrorStream());

                List<String> parts = new ArrayList<>();
                if (!stdout.isBlank()) {
                    parts.add(truncateOutput(stdout.strip()));
                }
                if (!stderr.isBlank()) {
                    parts.add("STDERR:\n" + truncateOutput(stderr.strip()));
                }
                if (process.exitValue() != 0) {
                    parts.add("\nExit code: " + process.exitValue());
                }
                return parts.isEmpty() ? "(no output)" : String.join("\n", parts);
            } catch (IOException e) {
                return "Error: failed to execute command: " + e.getMessage();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return "Error: command interrupted";
            }
        });
    }

    private static String readStream(java.io.InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String truncateOutput(String output) {
        if (output.length() <= MAX_OUTPUT_SIZE) {
            return output;
        }
        int half = MAX_OUTPUT_SIZE / 2;
        return output.substring(0, half) + "\n\n... (truncated) ...\n\n" + output.substring(output.length() - half);
    }
}

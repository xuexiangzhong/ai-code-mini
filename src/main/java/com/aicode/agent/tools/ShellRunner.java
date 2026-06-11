package com.aicode.agent.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Resolves a platform-appropriate shell for executing user commands.
 * Unix/macOS prefer bash (fallback sh); Windows prefers Git Bash when present, else PowerShell.
 */
public final class ShellRunner {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT)
            .contains("win");

    private static final List<String> WINDOWS_BASH_CANDIDATES = List.of(
            "bash",
            "C:\\Program Files\\Git\\bin\\bash.exe",
            "C:\\Program Files\\Git\\usr\\bin\\bash.exe",
            "C:\\Program Files (x86)\\Git\\bin\\bash.exe"
    );

    private static final List<String> UNIX_BASH_CANDIDATES = List.of(
            "bash",
            "/bin/bash",
            "/usr/bin/bash",
            "/usr/local/bin/bash"
    );

    private static final String RESOLVED_SHELL = resolveShellKind();

    public enum ShellKind {
        BASH("bash"),
        SH("sh"),
        POWERSHELL("PowerShell");

        private final String label;

        ShellKind(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record ShellInvocation(List<String> command, ShellKind kind) {}

    private ShellRunner() {}

    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    public static ShellKind activeShellKind() {
        return ShellKind.valueOf(RESOLVED_SHELL);
    }

    public static String activeShellDescription() {
        ShellKind kind = activeShellKind();
        if (kind == ShellKind.BASH) {
            return "bash (Unix shell)";
        }
        if (kind == ShellKind.SH) {
            return "sh (POSIX shell)";
        }
        return "PowerShell (Windows native shell; use PowerShell syntax when bash is unavailable)";
    }

    public static ShellInvocation resolve(String userCommand) {
        return switch (activeShellKind()) {
            case BASH -> new ShellInvocation(
                    List.of(resolveBashExecutable().orElse("bash"), "-c", userCommand),
                    ShellKind.BASH
            );
            case SH -> new ShellInvocation(List.of("sh", "-c", userCommand), ShellKind.SH);
            case POWERSHELL -> new ShellInvocation(
                    List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", userCommand),
                    ShellKind.POWERSHELL
            );
        };
    }

    private static String resolveShellKind() {
        if (findBashExecutable().isPresent()) {
            return ShellKind.BASH.name();
        }
        if (!IS_WINDOWS) {
            return ShellKind.SH.name();
        }
        return ShellKind.POWERSHELL.name();
    }

    private static Optional<String> findBashExecutable() {
        List<String> candidates = IS_WINDOWS ? WINDOWS_BASH_CANDIDATES : UNIX_BASH_CANDIDATES;
        for (String candidate : candidates) {
            if (isRunnable(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> resolveBashExecutable() {
        return findBashExecutable();
    }

    private static boolean isRunnable(String executable) {
        if (executable.contains("/") || executable.contains("\\")) {
            Path path = Path.of(executable);
            return Files.isRegularFile(path) && Files.isExecutable(path);
        }
        try {
            Process process = new ProcessBuilder(executable, "-c", "true")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /** Commands for cross-platform tests. */
    public static String sleepCommand(int seconds) {
        return activeShellKind() == ShellKind.POWERSHELL
                ? "Start-Sleep -Seconds " + seconds
                : "sleep " + seconds;
    }

    public static String stderrCommand(String message) {
        return activeShellKind() == ShellKind.POWERSHELL
                ? "[Console]::Error.WriteLine('" + message + "')"
                : "echo " + message + " >&2";
    }

    public static String noopCommand() {
        return activeShellKind() == ShellKind.POWERSHELL ? "$null" : "true";
    }

    public static String pipeToUpperCommand() {
        return activeShellKind() == ShellKind.POWERSHELL
                ? "'hello world' -replace 'hello','HELLO'"
                : "echo 'hello world' | tr 'a-z' 'A-Z'";
    }

    public static String expectedPipeToUpperResult() {
        return activeShellKind() == ShellKind.POWERSHELL ? "HELLO world" : "HELLO WORLD";
    }

    public static String multiLineEchoCommand() {
        return activeShellKind() == ShellKind.POWERSHELL
                ? "Write-Output 'line1'; Write-Output 'line2'"
                : "echo 'line1'; echo 'line2'";
    }
}

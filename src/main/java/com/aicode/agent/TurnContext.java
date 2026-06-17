package com.aicode.agent;

import com.aicode.agent.llm.Message;
import com.aicode.agent.tools.ShellRunner;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Per-turn dynamic environment injected only on the latest user message at API call time
 * (not persisted in conversation history). Static workspace/OS/shell live in the system prompt.
 */
public final class TurnContext {
    public static final int MAX_GIT_STATUS_LINES = 20;

    private final Path workspace;
    private final Path activeFile;

    private TurnContext(Path workspace, Path activeFile) {
        this.workspace = workspace.toAbsolutePath().normalize();
        this.activeFile = activeFile != null ? activeFile.toAbsolutePath().normalize() : null;
    }

    public static TurnContext of(Path workspace, Path activeFile) {
        return new TurnContext(workspace, activeFile);
    }

    public Path workspace() {
        return workspace;
    }

    public Path activeFile() {
        return activeFile;
    }

    /** Dynamic fields only: date, active file, git snapshot. */
    public String formatDynamicEnvironmentBlock() {
        StringBuilder sb = new StringBuilder();
        sb.append("<environment>\n");
        sb.append("- Date: ").append(LocalDate.now()).append('\n');
        if (activeFile != null && Files.exists(activeFile)) {
            appendActiveFile(sb);
        }
        appendGitLines(sb);
        appendMatchingRules(sb);
        sb.append("</environment>");
        return sb.toString();
    }

    private void appendMatchingRules(StringBuilder sb) {
        List<RuleContext.RuleMeta> rules = RuleContext.discover(workspace);
        String matching = RuleContext.formatMatchingGlobRules(workspace, activeFile, rules);
        if (matching == null || matching.isBlank()) {
            return;
        }
        sb.append("\n<active-rules>\n").append(matching).append("\n</active-rules>\n");
    }

    public String prependToUserMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return formatDynamicEnvironmentBlock();
        }
        return formatDynamicEnvironmentBlock() + "\n\n" + userMessage;
    }

    /** Inject environment on the last plain user message (ephemeral, not stored in history). */
    public static List<Message> injectOnLastUserMessage(List<Message> messages, TurnContext ctx) {
        if (ctx == null || messages.isEmpty()) {
            return messages;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (!"user".equals(message.role()) || !message.isStringContent()) {
                continue;
            }
            String text = message.contentText();
            if (text.startsWith("[Previous conversation summary]") || text.startsWith("<environment>")) {
                continue;
            }
            List<Message> copy = new ArrayList<>(messages);
            copy.set(i, Message.user(ctx.prependToUserMessage(text)));
            return copy;
        }
        return messages;
    }

    public static Safety.GitInfo collectGitInfo(Path workspace) {
        return GitInfoCache.get(workspace, () -> loadGitInfoUncached(workspace));
    }

    static Safety.GitInfo loadGitInfoUncached(Path workspace) {
        if (!Files.isDirectory(workspace.resolve(".git"))) {
            return Safety.parseGitInfo("", "", "", "");
        }
        String branch = runGit(workspace, "rev-parse", "--abbrev-ref", "HEAD");
        String lastCommit = runGit(workspace, "log", "-1", "--oneline");
        String status = limitGitStatus(runGit(workspace, "status", "--short"), MAX_GIT_STATUS_LINES);
        String remote = runGit(workspace, "remote", "get-url", "origin");
        return Safety.parseGitInfo(branch, lastCommit, status, remote);
    }

    static String limitGitStatus(String status, int maxLines) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String[] lines = status.split("\n");
        if (lines.length <= maxLines) {
            return status.strip();
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        sb.append("\n…(+").append(lines.length - maxLines).append(" more files)");
        return sb.toString();
    }

    private void appendActiveFile(StringBuilder sb) {
        try {
            Path rel = workspace.relativize(activeFile);
            sb.append("- Active file: ").append(rel.toString().replace('\\', '/')).append('\n');
        } catch (IllegalArgumentException ignored) {
            sb.append("- Active file: ").append(activeFile).append('\n');
        }
    }

    private void appendGitLines(StringBuilder sb) {
        Safety.GitInfo git = collectGitInfo(workspace);
        if (!git.branch().isEmpty()) {
            sb.append("- Branch: ").append(git.branch()).append('\n');
        }
        if (!git.lastCommit().isEmpty()) {
            sb.append("- Last commit: ").append(git.lastCommit()).append('\n');
        }
        if (!git.remoteUrl().isEmpty()) {
            sb.append("- Remote: ").append(git.remoteUrl()).append('\n');
        }
        if (!git.status().isEmpty()) {
            sb.append("- Git status:\n").append(git.status()).append('\n');
        }
    }

    private static String runGit(Path workspace, String... args) {
        try {
            String[] command = new String[args.length + 1];
            command[0] = "git";
            System.arraycopy(args, 0, command, 1, args.length);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workspace.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(2, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "";
            }
            if (process.exitValue() != 0) {
                return "";
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().reduce((a, b) -> a + "\n" + b).orElse("").strip();
            }
        } catch (Exception ignored) {
            return "";
        }
    }
}

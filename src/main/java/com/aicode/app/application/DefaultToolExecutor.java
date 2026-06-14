package com.aicode.app.application;

import com.aicode.agent.Agent;
import com.aicode.agent.Context;
import com.aicode.agent.Safety;
import com.aicode.agent.TaskManager;
import com.aicode.agent.llm.EmbeddingProvider;
import com.aicode.agent.tools.BashTool;
import com.aicode.agent.tools.DeleteTool;
import com.aicode.agent.tools.GlobTool;
import com.aicode.agent.tools.GrepTool;
import com.aicode.agent.tools.ListDirTool;
import com.aicode.agent.tools.ReadTool;
import com.aicode.agent.tools.SearchReplaceTool;
import com.aicode.agent.tools.SemanticSearchTool;
import com.aicode.agent.tools.WriteTool;
import com.aicode.app.approval.ApprovalGate;
import com.aicode.app.session.FileEditProposal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Core tool execution with sandbox and safety checks (no UI side effects).
 */
public final class DefaultToolExecutor implements Agent.ToolExecutor {
    private final WorkspaceGuard guard;
    private final TaskManager taskManager;
    private final Context.Scratchpad scratchpad;
    private final ApprovalGate approvalGate;
    private final Consumer<FileEditProposal> fileEditListener;
    private final EmbeddingProvider embeddingProvider;

    public DefaultToolExecutor(
            Path workspace,
            Safety.FileSystemSandbox sandbox,
            TaskManager taskManager,
            Context.Scratchpad scratchpad
    ) {
        this(workspace, sandbox, taskManager, scratchpad, null, null, null);
    }

    public DefaultToolExecutor(
            Path workspace,
            Safety.FileSystemSandbox sandbox,
            TaskManager taskManager,
            Context.Scratchpad scratchpad,
            ApprovalGate approvalGate
    ) {
        this(workspace, sandbox, taskManager, scratchpad, approvalGate, null, null);
    }

    public DefaultToolExecutor(
            Path workspace,
            Safety.FileSystemSandbox sandbox,
            TaskManager taskManager,
            Context.Scratchpad scratchpad,
            ApprovalGate approvalGate,
            Consumer<FileEditProposal> fileEditListener,
            EmbeddingProvider embeddingProvider
    ) {
        this.guard = new WorkspaceGuard(workspace, sandbox);
        this.taskManager = taskManager;
        this.scratchpad = scratchpad;
        this.approvalGate = approvalGate;
        this.fileEditListener = fileEditListener;
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public CompletableFuture<String> execute(String name, Map<String, Object> input) {
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

        Map<String, Object> resolvedInput = input;
        if ("read_file".equals(name) || "write_file".equals(name) || "search_replace".equals(name)
                || "delete_file".equals(name)) {
            String filePath = String.valueOf(input.getOrDefault("file_path", ""));
            String blocked = guard.validate(filePath);
            if (blocked != null) {
                return CompletableFuture.completedFuture(blocked);
            }
            resolvedInput = withPath(input, "file_path", guard.resolve(filePath).toString());
        }

        if ("glob".equals(name) || "grep".equals(name) || "list_dir".equals(name)) {
            String path = String.valueOf(input.getOrDefault("path", "."));
            String blocked = guard.validate(path);
            if (blocked != null) {
                return CompletableFuture.completedFuture(blocked);
            }
            resolvedInput = withPath(input, "path", guard.resolve(path).toString());
        }

        if ("semantic_search".equals(name)) {
            String path = String.valueOf(input.getOrDefault("path", "."));
            if (!".".equals(path) && !path.isBlank()) {
                String blocked = guard.validate(path);
                if (blocked != null) {
                    return CompletableFuture.completedFuture(blocked);
                }
                resolvedInput = withPath(input, "path", guard.resolve(path).toString());
            }
        }

        if ("bash".equals(name)) {
            String command = String.valueOf(input.getOrDefault("command", ""));
            String danger = Safety.checkDangerousCommand(command);
            if (danger != null) {
                Map<String, Object> bashInput = resolvedInput;
                if (approvalGate != null) {
                    return approvalGate.requestApproval(name, input, danger)
                            .thenCompose(approved -> {
                                if (!approved) {
                                    return CompletableFuture.completedFuture(
                                            "⚠️ Blocked: " + danger + ". User declined confirmation."
                                    );
                                }
                                return runTool(name, bashInput);
                            });
                }
                return CompletableFuture.completedFuture(
                        "⚠️ Blocked: " + danger + ". This command requires user confirmation."
                );
            }
        }

        return runTool(name, resolvedInput);
    }

    private CompletableFuture<String> runTool(String name, Map<String, Object> input) {
        try {
            if ("write_file".equals(name)) {
                return CompletableFuture.completedFuture(runWriteFile(input));
            }
            if ("search_replace".equals(name)) {
                return CompletableFuture.completedFuture(runSearchReplace(input));
            }
            String result = switch (name) {
                case "read_file" -> ReadTool.execute(ReadTool.Input.fromMap(input));
                case "delete_file" -> DeleteTool.execute(DeleteTool.Input.fromMap(input));
                case "bash" -> BashTool.execute(BashTool.Input.fromMap(input), guard.workspace()).join();
                case "glob" -> GlobTool.execute(GlobTool.Input.fromMap(input));
                case "grep" -> GrepTool.execute(GrepTool.Input.fromMap(input));
                case "list_dir" -> ListDirTool.execute(ListDirTool.Input.fromMap(input));
                case "semantic_search" -> SemanticSearchTool.execute(
                        SemanticSearchTool.Input.fromMap(input), guard.workspace(), embeddingProvider);
                default -> "Error: unknown tool \"" + name + "\"";
            };
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private String runWriteFile(Map<String, Object> input) {
        Path path = Path.of(String.valueOf(input.get("file_path")));
        String oldContent = readStringIfExists(path);
        boolean created = oldContent == null;
        String result = WriteTool.execute(WriteTool.Input.fromMap(input));
        if (!result.startsWith("Error:")) {
            notifyFileEdit(path, oldContent, readStringIfExists(path), created, result);
        }
        return result;
    }

    private String runSearchReplace(Map<String, Object> input) {
        Path path = Path.of(String.valueOf(input.get("file_path")));
        String oldContent = readStringIfExists(path);
        String result = SearchReplaceTool.execute(SearchReplaceTool.Input.fromMap(input));
        if (!result.startsWith("Error:") && result.contains("Updated")) {
            notifyFileEdit(path, oldContent, readStringIfExists(path), false, result);
        }
        return result;
    }

    private void notifyFileEdit(
            Path path,
            String oldContent,
            String newContent,
            boolean created,
            String result
    ) {
        if (fileEditListener == null || newContent == null) {
            return;
        }
        if (created && result.contains("(no changes)")) {
            return;
        }
        if (!created && oldContent != null && oldContent.equals(newContent)) {
            return;
        }
        fileEditListener.accept(FileEditProposal.create(path, oldContent, newContent, created));
    }

    private static String readStringIfExists(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.readString(path) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static Map<String, Object> withPath(Map<String, Object> input, String key, String value) {
        Map<String, Object> copy = new HashMap<>(input);
        copy.put(key, value);
        return copy;
    }
}

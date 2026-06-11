package com.aicode.app.application;

import com.aicode.agent.Agent;
import com.aicode.agent.Context;
import com.aicode.agent.Safety;
import com.aicode.agent.TaskManager;
import com.aicode.agent.tools.BashTool;
import com.aicode.agent.tools.GlobTool;
import com.aicode.agent.tools.GrepTool;
import com.aicode.agent.tools.ReadTool;
import com.aicode.agent.tools.WriteTool;
import com.aicode.app.approval.ApprovalGate;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Core tool execution with sandbox and safety checks (no UI side effects).
 */
public final class DefaultToolExecutor implements Agent.ToolExecutor {
    private final WorkspaceGuard guard;
    private final TaskManager taskManager;
    private final Context.Scratchpad scratchpad;
    private final ApprovalGate approvalGate;

    public DefaultToolExecutor(
            Path workspace,
            Safety.FileSystemSandbox sandbox,
            TaskManager taskManager,
            Context.Scratchpad scratchpad
    ) {
        this(workspace, sandbox, taskManager, scratchpad, null);
    }

    public DefaultToolExecutor(
            Path workspace,
            Safety.FileSystemSandbox sandbox,
            TaskManager taskManager,
            Context.Scratchpad scratchpad,
            ApprovalGate approvalGate
    ) {
        this.guard = new WorkspaceGuard(workspace, sandbox);
        this.taskManager = taskManager;
        this.scratchpad = scratchpad;
        this.approvalGate = approvalGate;
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
        if ("read_file".equals(name) || "write_file".equals(name)) {
            String filePath = String.valueOf(input.getOrDefault("file_path", ""));
            String blocked = guard.validate(filePath);
            if (blocked != null) {
                return CompletableFuture.completedFuture(blocked);
            }
            resolvedInput = withPath(input, "file_path", guard.resolve(filePath).toString());
        }

        if ("glob".equals(name) || "grep".equals(name)) {
            String path = String.valueOf(input.getOrDefault("path", "."));
            String blocked = guard.validate(path);
            if (blocked != null) {
                return CompletableFuture.completedFuture(blocked);
            }
            resolvedInput = withPath(input, "path", guard.resolve(path).toString());
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
            String result = switch (name) {
                case "read_file" -> ReadTool.execute(ReadTool.Input.fromMap(input));
                case "write_file" -> WriteTool.execute(WriteTool.Input.fromMap(input));
                case "bash" -> BashTool.execute(BashTool.Input.fromMap(input), guard.workspace()).join();
                case "glob" -> GlobTool.execute(GlobTool.Input.fromMap(input));
                case "grep" -> GrepTool.execute(GrepTool.Input.fromMap(input));
                default -> "Error: unknown tool \"" + name + "\"";
            };
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static Map<String, Object> withPath(Map<String, Object> input, String key, String value) {
        Map<String, Object> copy = new HashMap<>(input);
        copy.put(key, value);
        return copy;
    }
}

package com.aicode.agent;

import com.aicode.agent.llm.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TaskManager {
    public enum TaskStatus {
        PENDING("pending"),
        IN_PROGRESS("in_progress"),
        COMPLETED("completed"),
        FAILED("failed");

        private final String value;

        TaskStatus(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static TaskStatus fromString(String s) {
            for (TaskStatus status : values()) {
                if (status.value.equals(s)) {
                    return status;
                }
            }
            throw new IllegalArgumentException("Unknown status: " + s);
        }
    }

    public record Task(String id, String description, TaskStatus status) {
        public Task(String id, String description) {
            this(id, description, TaskStatus.PENDING);
        }
    }

    private final List<Task> tasks = new ArrayList<>();
    private int nextId = 1;

    public String create(String description) {
        String taskId = "task_" + nextId++;
        tasks.add(new Task(taskId, description));
        return taskId;
    }

    public boolean update(String taskId, TaskStatus status) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id().equals(taskId)) {
                Task t = tasks.get(i);
                tasks.set(i, new Task(t.id(), t.description(), status));
                return true;
            }
        }
        return false;
    }

    public Task get(String taskId) {
        return tasks.stream().filter(t -> t.id().equals(taskId)).findFirst().orElse(null);
    }

    public List<Task> list(TaskStatus status) {
        if (status == null) {
            return List.copyOf(tasks);
        }
        return tasks.stream().filter(t -> t.status() == status).toList();
    }

    public String formatForLLM() {
        if (tasks.isEmpty()) {
            return "(no tasks)";
        }
        StringBuilder sb = new StringBuilder();
        for (Task t : tasks) {
            String icon = switch (t.status()) {
                case COMPLETED -> "[x]";
                case IN_PROGRESS -> "[~]";
                case FAILED -> "[!]";
                default -> "[ ]";
            };
            sb.append(icon).append(" ").append(t.id()).append(": ").append(t.description()).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    public void clear() {
        tasks.clear();
        nextId = 1;
    }

    public int length() {
        return tasks.size();
    }

    public static final Tool TASK_CREATE_TOOL = new Tool(
            "task_create",
            "Create a new task in the plan. Returns the task ID.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "description", Map.of(
                                    "type", "string",
                                    "description", "Description of the task to create"
                            )
                    ),
                    "required", List.of("description")
            )
    );

    public static final Tool TASK_UPDATE_TOOL = new Tool(
            "task_update",
            "Update the status of an existing task. Status can be \"pending\", \"in_progress\", \"completed\", or \"failed\".",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "id", Map.of("type", "string", "description", "The task ID to update"),
                            "status", Map.of(
                                    "type", "string",
                                    "enum", List.of("pending", "in_progress", "completed", "failed"),
                                    "description", "The new status for the task"
                            )
                    ),
                    "required", List.of("id", "status")
            )
    );

    public static final Tool TASK_LIST_TOOL = new Tool(
            "task_list",
            "List all tasks in the current plan with their status. Optionally filter by status.",
            Map.of(
                    "type", "object",
                    "properties", Map.of(
                            "status", Map.of(
                                    "type", "string",
                                    "enum", List.of("pending", "in_progress", "completed", "failed"),
                                    "description", "Filter tasks by status (optional)"
                            )
                    )
            )
    );

    public static final List<Tool> TASK_TOOLS = List.of(
            TASK_CREATE_TOOL, TASK_UPDATE_TOOL, TASK_LIST_TOOL
    );

    public static String executeTaskTool(TaskManager manager, String name, Map<String, Object> input) {
        return switch (name) {
            case "task_create" -> {
                Object desc = input.get("description");
                if (desc == null || desc.toString().isBlank()) {
                    yield "Error: description is required";
                }
                String taskId = manager.create(desc.toString());
                yield "Created " + taskId + ": " + desc;
            }
            case "task_update" -> {
                String taskId = String.valueOf(input.getOrDefault("id", ""));
                String statusStr = String.valueOf(input.getOrDefault("status", ""));
                if (taskId.isBlank() || statusStr.isBlank()) {
                    yield "Error: id and status are required";
                }
                TaskStatus status = TaskStatus.fromString(statusStr);
                boolean ok = manager.update(taskId, status);
                yield ok ? "Updated " + taskId + " → " + statusStr
                        : "Error: task " + taskId + " not found";
            }
            case "task_list" -> {
                Object statusObj = input.get("status");
                TaskStatus filter = statusObj != null ? TaskStatus.fromString(statusObj.toString()) : null;
                List<Task> list = manager.list(filter);
                if (list.isEmpty()) {
                    yield "(no tasks)";
                }
                StringBuilder sb = new StringBuilder();
                for (Task t : list) {
                    sb.append(t.id()).append(" [").append(t.status().value())
                            .append("]: ").append(t.description()).append("\n");
                }
                yield sb.toString().stripTrailing();
            }
            default -> "Error: unknown task tool \"" + name + "\"";
        };
    }
}

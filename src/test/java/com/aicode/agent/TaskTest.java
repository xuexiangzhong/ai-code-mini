package com.aicode.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {
    @Nested
    class TestTaskManager {
        TaskManager manager;

        @BeforeEach
        void setUp() {
            manager = new TaskManager();
        }

        @Test
        void createTasksWithIncrementalIds() {
            String id1 = manager.create("First task");
            String id2 = manager.create("Second task");
            assertEquals("task_1", id1);
            assertEquals("task_2", id2);
            assertEquals(2, manager.length());
        }

        @Test
        void createTasksWithPendingStatus() {
            manager.create("My task");
            TaskManager.Task task = manager.get("task_1");
            assertNotNull(task);
            assertEquals(TaskManager.TaskStatus.PENDING, task.status());
        }

        @Test
        void updateTaskStatus() {
            manager.create("My task");
            boolean ok = manager.update("task_1", TaskManager.TaskStatus.IN_PROGRESS);
            assertTrue(ok);
            assertEquals(TaskManager.TaskStatus.IN_PROGRESS, manager.get("task_1").status());
        }

        @Test
        void returnFalseForNonexistentTask() {
            assertFalse(manager.update("task_999", TaskManager.TaskStatus.COMPLETED));
        }

        @Test
        void listAllTasks() {
            manager.create("Task A");
            manager.create("Task B");
            assertEquals(2, manager.list(null).size());
        }

        @Test
        void filterTasksByStatus() {
            manager.create("Task A");
            manager.create("Task B");
            manager.update("task_1", TaskManager.TaskStatus.COMPLETED);
            assertEquals(1, manager.list(TaskManager.TaskStatus.COMPLETED).size());
            assertEquals(1, manager.list(TaskManager.TaskStatus.PENDING).size());
        }

        @Test
        void formatForLlm() {
            manager.create("Read the file");
            manager.create("Write the output");
            manager.update("task_1", TaskManager.TaskStatus.COMPLETED);
            String formatted = manager.formatForLLM();
            assertTrue(formatted.contains("[x] task_1: Read the file"));
            assertTrue(formatted.contains("[ ] task_2: Write the output"));
        }

        @Test
        void formatEmptyTasks() {
            assertEquals("(no tasks)", manager.formatForLLM());
        }

        @Test
        void formatInProgressAndFailed() {
            manager.create("In progress");
            manager.create("Failed");
            manager.update("task_1", TaskManager.TaskStatus.IN_PROGRESS);
            manager.update("task_2", TaskManager.TaskStatus.FAILED);
            String formatted = manager.formatForLLM();
            assertTrue(formatted.contains("[~] task_1"));
            assertTrue(formatted.contains("[!] task_2"));
        }

        @Test
        void clearAllTasks() {
            manager.create("Task A");
            manager.create("Task B");
            manager.clear();
            assertEquals(0, manager.length());
            String newId = manager.create("New task");
            assertEquals("task_1", newId);
        }

        @Test
        void getNonexistentTask() {
            assertNull(manager.get("task_999"));
        }
    }

    @Nested
    class TestExecuteTaskTool {
        TaskManager manager;

        @BeforeEach
        void setUp() {
            manager = new TaskManager();
        }

        @Test
        void createTask() {
            String result = TaskManager.executeTaskTool(
                    manager, "task_create", Map.of("description", "Write tests"));
            assertEquals("Created task_1: Write tests", result);
            assertEquals(1, manager.length());
        }

        @Test
        void errorForMissingDescription() {
            String result = TaskManager.executeTaskTool(manager, "task_create", Map.of());
            assertTrue(result.contains("Error"));
        }

        @Test
        void updateTask() {
            manager.create("My task");
            String result = TaskManager.executeTaskTool(
                    manager, "task_update", Map.of("id", "task_1", "status", "completed"));
            assertTrue(result.contains("Updated task_1"));
        }

        @Test
        void errorForNonexistentUpdate() {
            String result = TaskManager.executeTaskTool(
                    manager, "task_update", Map.of("id", "task_999", "status", "completed"));
            assertTrue(result.contains("not found"));
        }

        @Test
        void listTasks() {
            manager.create("Task A");
            manager.create("Task B");
            String result = TaskManager.executeTaskTool(manager, "task_list", Map.of());
            assertTrue(result.contains("task_1"));
            assertTrue(result.contains("task_2"));
        }

        @Test
        void listFilteredTasks() {
            manager.create("Task A");
            manager.create("Task B");
            manager.update("task_1", TaskManager.TaskStatus.COMPLETED);
            String result = TaskManager.executeTaskTool(
                    manager, "task_list", Map.of("status", "completed"));
            assertTrue(result.contains("task_1"));
            assertFalse(result.contains("task_2"));
        }

        @Test
        void emptyList() {
            String result = TaskManager.executeTaskTool(manager, "task_list", Map.of());
            assertEquals("(no tasks)", result);
        }

        @Test
        void unknownTool() {
            String result = TaskManager.executeTaskTool(manager, "unknown_tool", Map.of());
            assertTrue(result.contains("Error"));
        }
    }

    @Nested
    class TestToolDefinitions {
        @Test
        void correctToolNames() {
            assertEquals("task_create", TaskManager.TASK_CREATE_TOOL.name());
            assertEquals("task_update", TaskManager.TASK_UPDATE_TOOL.name());
            assertEquals("task_list", TaskManager.TASK_LIST_TOOL.name());
        }

        @Test
        void allToolsInList() {
            assertEquals(3, TaskManager.TASK_TOOLS.size());
            assertEquals(List.of("task_create", "task_update", "task_list"),
                    TaskManager.TASK_TOOLS.stream().map(t -> t.name()).toList());
        }
    }
}

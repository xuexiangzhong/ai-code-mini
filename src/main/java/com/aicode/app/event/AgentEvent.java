package com.aicode.app.event;

import java.util.Map;

public sealed interface AgentEvent permits
        AgentEvent.TextDelta,
        AgentEvent.TextDone,
        AgentEvent.ToolCallStarted,
        AgentEvent.ToolCallFinished,
        AgentEvent.FileEditProposed,
        AgentEvent.ApprovalRequired,
        AgentEvent.ApprovalResolved,
        AgentEvent.Error,
        AgentEvent.OutputTruncated,
        AgentEvent.Cancelled,
        AgentEvent.Done {

    record TextDelta(String delta) implements AgentEvent {}

    record TextDone(String fullText) implements AgentEvent {}

    record ToolCallStarted(String toolName, Map<String, Object> input, String callId) implements AgentEvent {}

    record ToolCallFinished(String toolName, String callId, String result, long durationMs) implements AgentEvent {}

    record FileEditProposed(
            String editId,
            String filePath,
            String oldContent,
            String newContent,
            boolean created,
            String diff
    ) implements AgentEvent {}

    record ApprovalRequired(
            String approvalId,
            String toolName,
            Map<String, Object> input,
            String reason
    ) implements AgentEvent {}

    record ApprovalResolved(String approvalId, boolean approved) implements AgentEvent {}

    record Error(String message, String code) implements AgentEvent {}

    /** Emitted when LLM output hits max_tokens; {@code retrying} indicates an automatic retry is in progress. */
    record OutputTruncated(String message, int maxTokens, boolean retrying) implements AgentEvent {}

    record Cancelled(String partialText) implements AgentEvent {}

    record Done(int iterations, int inputTokens, int outputTokens) implements AgentEvent {}
}

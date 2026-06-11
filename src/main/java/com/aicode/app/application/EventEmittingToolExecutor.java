package com.aicode.app.application;

import com.aicode.agent.Agent;
import com.aicode.app.event.AgentEvent;
import com.aicode.app.event.AgentEventListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EventEmittingToolExecutor implements Agent.ToolExecutor {
    private final Agent.ToolExecutor delegate;
    private final AgentEventListener listener;

    public EventEmittingToolExecutor(Agent.ToolExecutor delegate, AgentEventListener listener) {
        this.delegate = delegate;
        this.listener = listener != null ? listener : AgentEventListener.NOOP;
    }

    @Override
    public CompletableFuture<String> execute(String name, Map<String, Object> input) {
        String callId = UUID.randomUUID().toString();
        listener.onEvent(new AgentEvent.ToolCallStarted(name, input, callId));
        long start = System.nanoTime();
        return delegate.execute(name, input).whenComplete((result, error) -> {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            if (error != null) {
                listener.onEvent(new AgentEvent.Error(error.getMessage(), "TOOL_ERROR"));
            } else {
                listener.onEvent(new AgentEvent.ToolCallFinished(name, callId, result, durationMs));
            }
        });
    }
}

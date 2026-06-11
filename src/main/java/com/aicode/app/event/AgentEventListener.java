package com.aicode.app.event;

@FunctionalInterface
public interface AgentEventListener {
    void onEvent(AgentEvent event);

    AgentEventListener NOOP = event -> {};
}

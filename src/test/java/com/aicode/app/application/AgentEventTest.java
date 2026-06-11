package com.aicode.app.application;

import com.aicode.agent.Agent;
import com.aicode.app.event.AgentEvent;
import com.aicode.app.event.AgentEventListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AgentEventTest {

    @Test
    void eventEmittingToolExecutorEmitsStartAndFinish() {
        List<AgentEvent> events = new ArrayList<>();
        AgentEventListener listener = events::add;
        Agent.ToolExecutor delegate = (name, input) -> CompletableFuture.completedFuture("ok");
        EventEmittingToolExecutor executor = new EventEmittingToolExecutor(delegate, listener);

        String result = executor.execute("read_file", Map.of("file_path", "pom.xml")).join();

        assertEquals("ok", result);
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.ToolCallStarted));
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.ToolCallFinished));
    }
}

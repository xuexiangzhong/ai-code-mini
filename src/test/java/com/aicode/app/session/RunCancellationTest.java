package com.aicode.app.session;

import com.aicode.agent.Agent;
import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.StreamEvent;
import com.aicode.app.event.AgentEvent;
import com.aicode.app.event.AgentEventListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RunCancellationTest {

    @Test
    void cancelSessionStopsAgentRun() {
        RunCancellation cancellation = new RunCancellation();
        AtomicBoolean streamStarted = new AtomicBoolean(false);
        LLMProvider provider = new LLMProvider() {
            @Override
            public CompletableFuture<com.aicode.agent.llm.ChatResponse> chat(
                    List<Message> messages,
                    ChatOptions options
            ) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void stream(List<Message> messages, ChatOptions options, java.util.function.Consumer<StreamEvent> consumer) {
                streamStarted.set(true);
                consumer.accept(new StreamEvent("text_delta", "partial"));
                while (!cancellation.isCancelled()) {
                    Thread.onSpinWait();
                }
                throw new RunCancelledException("partial");
            }
        };

        List<AgentEvent> events = new ArrayList<>();
        AgentEventListener listener = events::add;
        Agent.AgentConfig config = new Agent.AgentConfig(
                provider,
                "system",
                List.of(),
                (name, input) -> CompletableFuture.completedFuture("ok"),
                3,
                com.aicode.agent.llm.OutputTokenLimits.defaults(),
                false,
                cancellation
        );

        CompletableFuture<Agent.AgentResult> future = Agent.runAgentStream(
                config,
                List.of(Message.user("hello")),
                listener
        );
        assertTrue(streamStarted.get());
        cancellation.cancel();
        Agent.AgentResult result = future.join();
        assertEquals("partial", result.text());
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.Cancelled));
    }
}

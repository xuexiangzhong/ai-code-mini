package com.aicode.app.event;

import com.aicode.agent.Agent;
import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AgentStreamTest {

    @Test
    void emitsTextDeltaEvents() {
        LLMProvider provider = new LLMProvider() {
            @Override
            public CompletableFuture<ChatResponse> chat(List<Message> messages, com.aicode.agent.llm.ChatOptions options) {
                return CompletableFuture.completedFuture(
                        new ChatResponse(
                                List.of(new TextBlock("Hi")),
                                "Hi",
                                "end_turn",
                                Map.of("input_tokens", 1, "output_tokens", 1)
                        )
                );
            }

            @Override
            public void stream(List<Message> messages, com.aicode.agent.llm.ChatOptions options,
                               java.util.function.Consumer<com.aicode.agent.llm.StreamEvent> consumer) {
                consumer.accept(new com.aicode.agent.llm.StreamEvent("text_delta", "H"));
                consumer.accept(new com.aicode.agent.llm.StreamEvent("text_delta", "i"));
                consumer.accept(new com.aicode.agent.llm.StreamEvent(
                        "message_stop",
                        "Hi",
                        new ChatResponse(
                                List.of(new TextBlock("Hi")),
                                "Hi",
                                "end_turn",
                                Map.of("input_tokens", 1, "output_tokens", 1)
                        )
                ));
            }
        };
        List<AgentEvent> events = new ArrayList<>();
        AgentEventListener listener = events::add;

        Agent.AgentResult result = Agent.runAgentStream(
                new Agent.AgentConfig(provider, "system", List.of(), (n, i) -> CompletableFuture.completedFuture("")),
                List.of(Message.user("hello")),
                listener
        ).join();

        assertEquals("Hi", result.text());
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.TextDelta));
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.TextDone));
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.Done));
    }
}

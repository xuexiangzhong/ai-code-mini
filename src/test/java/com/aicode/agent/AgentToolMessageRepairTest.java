package com.aicode.agent;

import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;
import com.aicode.app.session.RunCancellation;
import com.aicode.app.session.RunCancelledException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class AgentToolMessageRepairTest {
    @Test
    void cancellationPreservesCompletedToolResultInAppendedMessages() {
        RunCancellation cancellation = new RunCancellation();
        LLMProvider provider = new LLMProvider() {
            @Override
            public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
                return CompletableFuture.completedFuture(
                        new ChatResponse(
                                List.of(
                                        new ToolUseBlock("call_1", "read_file", Map.of("file_path", "a.txt")),
                                        new ToolUseBlock("call_2", "grep", Map.of("pattern", "foo"))
                                ),
                                "",
                                "tool_use",
                                Map.of("input_tokens", 1, "output_tokens", 1)
                        )
                );
            }

            @Override
            public void stream(
                    List<Message> messages,
                    ChatOptions options,
                    java.util.function.Consumer<com.aicode.agent.llm.StreamEvent> consumer
            ) {
                throw new UnsupportedOperationException();
            }
        };
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        Agent.AgentConfig config = new Agent.AgentConfig(
                provider,
                "system",
                List.of(),
                (name, input) -> {
                    if (calls.incrementAndGet() == 1) {
                        return CompletableFuture.completedFuture("contents of a.txt");
                    }
                    cancellation.cancel();
                    return CompletableFuture.failedFuture(new RunCancelledException("partial"));
                },
                3,
                com.aicode.agent.llm.OutputTokenLimits.defaults(),
                false,
                cancellation
        );

        Agent.AgentResult result = Agent.runAgent(
                config,
                List.of(Message.user("read and search"))
        ).join();

        ToolResultBlock first = result.appendedMessages().stream()
                .filter(ToolMessageRepair::isToolResultUser)
                .flatMap(m -> m.contentBlocks().stream())
                .filter(ToolResultBlock.class::isInstance)
                .map(ToolResultBlock.class::cast)
                .filter(trb -> "call_1".equals(trb.toolUseId()))
                .findFirst()
                .orElseThrow();
        ToolResultBlock second = result.appendedMessages().stream()
                .filter(ToolMessageRepair::isToolResultUser)
                .flatMap(m -> m.contentBlocks().stream())
                .filter(ToolResultBlock.class::isInstance)
                .map(ToolResultBlock.class::cast)
                .filter(trb -> "call_2".equals(trb.toolUseId()))
                .findFirst()
                .orElseThrow();

        assertEquals("contents of a.txt", first.content());
        assertFalse(first.isError());
        assertTrue(second.isError());
    }
}

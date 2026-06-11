package com.aicode.agent;

import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.OutputTokenLimits;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.ToolUseBlock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aicode.agent.TestFixtures.TEST_TOOL;
import static org.junit.jupiter.api.Assertions.*;

class ParallelTest {
    @Nested
    class ParallelToolCalls {
        @Test
        void executeConcurrentlyWhenEnabled() {
            LLMProvider provider = mockProvider(List.of(
                    new ChatResponse(
                            List.of(
                                    new ToolUseBlock("c1", "test_tool", Map.of("query", "a")),
                                    new ToolUseBlock("c2", "test_tool", Map.of("query", "b")),
                                    new ToolUseBlock("c3", "test_tool", Map.of("query", "c"))
                            ),
                            "", "tool_use", Map.of("input_tokens", 10, "output_tokens", 10)
                    ),
                    new ChatResponse(
                            List.of(new TextBlock("All done.")), "All done.", "end_turn",
                            Map.of("input_tokens", 20, "output_tokens", 5)
                    )
            ));
            List<Long> startTimes = new ArrayList<>();
            Agent.AgentConfig config = new Agent.AgentConfig(
                    provider, "test", List.of(TEST_TOOL),
                    (name, input) -> CompletableFuture.supplyAsync(() -> {
                        startTimes.add(System.nanoTime());
                        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return "result-" + input.get("query");
                    }),
                    10, OutputTokenLimits.defaults(), true
            );
            Agent.AgentResult result = Agent.runAgent(config, "Do three things").join();
            assertEquals(3, result.toolCalls().size());
            assertEquals("result-a", result.toolCalls().get(0).result());
            assertEquals("result-b", result.toolCalls().get(1).result());
            assertEquals("result-c", result.toolCalls().get(2).result());
            long spanMs = (startTimes.stream().max(Long::compare).orElse(0L)
                    - startTimes.stream().min(Long::compare).orElse(0L)) / 1_000_000;
            assertTrue(spanMs < 40, "span=" + spanMs);
        }

        @Test
        void executeSequentiallyWhenDisabled() {
            LLMProvider provider = mockProvider(List.of(
                    new ChatResponse(
                            List.of(
                                    new ToolUseBlock("c1", "test_tool", Map.of("query", "a")),
                                    new ToolUseBlock("c2", "test_tool", Map.of("query", "b"))
                            ),
                            "", "tool_use", Map.of("input_tokens", 10, "output_tokens", 10)
                    ),
                    new ChatResponse(
                            List.of(new TextBlock("Done.")), "Done.", "end_turn",
                            Map.of("input_tokens", 20, "output_tokens", 5)
                    )
            ));
            List<Long> startTimes = new ArrayList<>();
            Agent.AgentConfig config = new Agent.AgentConfig(
                    provider, "test", List.of(TEST_TOOL),
                    (name, input) -> CompletableFuture.supplyAsync(() -> {
                        startTimes.add(System.nanoTime());
                        try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return "result-" + input.get("query");
                    }),
                    10, OutputTokenLimits.defaults(), false
            );
            Agent.runAgent(config, "Do two things").join();
            assertTrue((startTimes.get(1) - startTimes.get(0)) / 1_000_000 >= 40);
        }

        @Test
        void preserveResultOrder() {
            LLMProvider provider = mockProvider(List.of(
                    new ChatResponse(
                            List.of(
                                    new ToolUseBlock("c1", "test_tool", Map.of("query", "slow")),
                                    new ToolUseBlock("c2", "test_tool", Map.of("query", "fast"))
                            ),
                            "", "tool_use", Map.of("input_tokens", 10, "output_tokens", 10)
                    ),
                    new ChatResponse(
                            List.of(new TextBlock("OK")), "OK", "end_turn",
                            Map.of("input_tokens", 10, "output_tokens", 5)
                    )
            ));
            Agent.AgentConfig config = new Agent.AgentConfig(
                    provider, "test", List.of(TEST_TOOL),
                    (name, input) -> CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep("slow".equals(input.get("query")) ? 80 : 10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "done-" + input.get("query");
                    }),
                    10, OutputTokenLimits.defaults(), true
            );
            Agent.AgentResult result = Agent.runAgent(config, "Order test").join();
            assertEquals("done-slow", result.toolCalls().get(0).result());
            assertEquals("done-fast", result.toolCalls().get(1).result());
        }

        @Test
        void singleToolCallWithParallelEnabled() {
            LLMProvider provider = mockProvider(List.of(
                    new ChatResponse(
                            List.of(new ToolUseBlock("c1", "test_tool", Map.of("query", "only"))),
                            "", "tool_use", Map.of("input_tokens", 10, "output_tokens", 10)
                    ),
                    new ChatResponse(
                            List.of(new TextBlock("OK")), "OK", "end_turn",
                            Map.of("input_tokens", 10, "output_tokens", 5)
                    )
            ));
            AtomicInteger callCount = new AtomicInteger();
            Agent.AgentConfig config = new Agent.AgentConfig(
                    provider, "test", List.of(TEST_TOOL),
                    (name, input) -> {
                        callCount.incrementAndGet();
                        return CompletableFuture.completedFuture("result");
                    },
                    10, OutputTokenLimits.defaults(), true
            );
            Agent.AgentResult result = Agent.runAgent(config, "Single tool").join();
            assertEquals(1, result.toolCalls().size());
            assertEquals(1, callCount.get());
        }
    }

    private static com.aicode.agent.llm.LLMProvider mockProvider(List<ChatResponse> responses) {
        return TestFixtures.mockProvider(responses);
    }
}

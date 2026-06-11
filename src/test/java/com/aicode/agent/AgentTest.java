package com.aicode.agent;

import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.OutputTokenLimits;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.aicode.agent.TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentTest {
    @Nested
    class RunAgent {
        @Test
        void returnTextWithoutToolUse() {
            LLMProvider provider = mockProvider(List.of(
                    new ChatResponse(
                            List.of(new TextBlock("Hello!")), "Hello!", "end_turn",
                            Map.of("input_tokens", 10, "output_tokens", 5)
                    )
            ));
            Agent.AgentResult result = Agent.runAgent(baseConfig(provider, null), "Hi").join();
            assertEquals("Hello!", result.text());
            assertTrue(result.toolCalls().isEmpty());
            assertEquals(1, result.iterations());
        }

        @Test
        void executeToolCallsAndContinue() {
            LLMProvider provider = mockProvider(List.of(
                    new ChatResponse(
                            List.of(
                                    new TextBlock("Let me check."),
                                    new ToolUseBlock("call_1", "test_tool", Map.of("query", "hello"))
                            ),
                            "Let me check.", "tool_use",
                            Map.of("input_tokens", 15, "output_tokens", 10)
                    ),
                    new ChatResponse(
                            List.of(new TextBlock("The result is ready.")), "The result is ready.", "end_turn",
                            Map.of("input_tokens", 30, "output_tokens", 8)
                    )
            ));
            List<String> calls = new ArrayList<>();
            Agent.ToolExecutor exec = (name, input) -> {
                calls.add(name);
                return CompletableFuture.completedFuture("tool output");
            };
            Agent.AgentResult result = Agent.runAgent(baseConfig(provider, exec), "Check something").join();
            assertEquals("The result is ready.", result.text());
            assertEquals(1, result.toolCalls().size());
            assertEquals("test_tool", result.toolCalls().getFirst().name());
            assertEquals(Map.of("query", "hello"), result.toolCalls().getFirst().input());
            assertEquals("tool output", result.toolCalls().getFirst().result());
            assertEquals(2, result.iterations());
            assertEquals(List.of("test_tool"), calls);
        }

        @Test
        void handleMultipleToolCalls() {
            LLMProvider provider = mockProvider(List.of(
                    new ChatResponse(
                            List.of(
                                    new ToolUseBlock("call_1", "test_tool", Map.of("query", "a")),
                                    new ToolUseBlock("call_2", "test_tool", Map.of("query", "b"))
                            ),
                            "", "tool_use", Map.of("input_tokens", 10, "output_tokens", 10)
                    ),
                    new ChatResponse(
                            List.of(new TextBlock("Done.")), "Done.", "end_turn",
                            Map.of("input_tokens", 20, "output_tokens", 5)
                    )
            ));
            Agent.AgentResult result = Agent.runAgent(
                    baseConfig(provider, (name, input) ->
                            CompletableFuture.completedFuture("result-" + input.get("query"))),
                    "Do two things"
            ).join();
            assertEquals(2, result.toolCalls().size());
            assertEquals("result-a", result.toolCalls().get(0).result());
            assertEquals("result-b", result.toolCalls().get(1).result());
        }

        @Test
        void stopAtMaxIterations() {
            ChatResponse infinite = new ChatResponse(
                    List.of(new ToolUseBlock("call_inf", "test_tool", Map.of("query", "loop"))),
                    "", "tool_use", Map.of("input_tokens", 5, "output_tokens", 5)
            );
            LLMProvider provider = mockProvider(List.of(infinite, infinite, infinite));
            Agent.AgentConfig config = new Agent.AgentConfig(
                    provider, "sys", List.of(TEST_TOOL),
                    (name, input) -> CompletableFuture.completedFuture("ok"),
                    3, OutputTokenLimits.defaults(), false
            );
            Agent.AgentResult result = Agent.runAgent(config, "Loop forever").join();
            assertEquals("(max iterations reached)", result.text());
            assertEquals(3, result.iterations());
            assertEquals(3, result.toolCalls().size());
        }

        @Test
        void accumulateTokenUsage() {
            LLMProvider provider = mockProvider(List.of(
                    new ChatResponse(
                            List.of(new ToolUseBlock("c1", "test_tool", Map.of("query", "x"))),
                            "", "tool_use", Map.of("input_tokens", 100, "output_tokens", 50)
                    ),
                    new ChatResponse(
                            List.of(new TextBlock("Final.")), "Final.", "end_turn",
                            Map.of("input_tokens", 200, "output_tokens", 30)
                    )
            ));
            Agent.AgentResult result = Agent.runAgent(baseConfig(provider, null), "Count tokens").join();
            assertEquals(300, result.inputTokens());
            assertEquals(80, result.outputTokens());
        }

        @Test
        void buildCorrectMessageHistory() {
            CopyOnWriteArrayList<List<Message>> calls = new CopyOnWriteArrayList<>();
            LLMProvider provider = trackingProvider(List.of(
                    new ChatResponse(
                            List.of(
                                    new TextBlock("Checking..."),
                                    new ToolUseBlock("c1", "test_tool", Map.of("query", "test"))
                            ),
                            "Checking...", "tool_use", Map.of("input_tokens", 10, "output_tokens", 10)
                    ),
                    new ChatResponse(
                            List.of(new TextBlock("Done.")), "Done.", "end_turn",
                            Map.of("input_tokens", 20, "output_tokens", 5)
                    )
            ), calls);
            Agent.runAgent(baseConfig(provider, null), "Do it").join();
            List<Message> secondCall = calls.get(1);
            assertEquals(3, secondCall.size());
            assertEquals("Do it", secondCall.get(0).contentText());
            assertEquals("assistant", secondCall.get(1).role());
            assertEquals("user", secondCall.get(2).role());
            assertInstanceOf(ToolResultBlock.class, secondCall.get(2).contentBlocks().getFirst());
        }

        @Test
        void emptyResponse() {
            LLMProvider provider = mockProvider(List.of(
                    new ChatResponse(List.of(), "", "end_turn", Map.of("input_tokens", 5, "output_tokens", 0))
            ));
            Agent.AgentResult result = Agent.runAgent(baseConfig(provider, null), "Empty").join();
            assertEquals("", result.text());
            assertEquals(1, result.iterations());
        }

        @Test
        void retryWhenOutputTruncated() {
            LLMProvider provider = mockProvider(List.of(
                    new ChatResponse(
                            List.of(new TextBlock("partial")), "partial", "max_tokens",
                            Map.of("input_tokens", 10, "output_tokens", 8192)
                    ),
                    new ChatResponse(
                            List.of(new TextBlock("complete answer")), "complete answer", "end_turn",
                            Map.of("input_tokens", 10, "output_tokens", 100)
                    )
            ));
            Agent.AgentConfig config = new Agent.AgentConfig(
                    provider,
                    "sys",
                    List.of(),
                    (name, input) -> CompletableFuture.completedFuture(""),
                    10,
                    new OutputTokenLimits(8192, 32768, 2),
                    false
            );
            Agent.AgentResult result = Agent.runAgent(config, "Long task").join();
            assertEquals("complete answer", result.text());
            assertEquals(1, result.iterations());
        }
    }
}

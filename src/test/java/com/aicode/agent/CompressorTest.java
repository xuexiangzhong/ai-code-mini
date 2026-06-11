package com.aicode.agent;

import com.aicode.agent.llm.ChatOptions;
import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class CompressorTest {
    static LLMProvider mockProvider(String summaryText) {
        return new LLMProvider() {
            @Override
            public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
                return CompletableFuture.completedFuture(new ChatResponse(
                        List.of(new TextBlock(summaryText)),
                        summaryText,
                        "end_turn",
                        Map.of("input_tokens", 100, "output_tokens", 20)
                ));
            }

            @Override
            public void stream(List<Message> messages, ChatOptions options, Consumer<com.aicode.agent.llm.StreamEvent> consumer) {
                throw new UnsupportedOperationException();
            }
        };
    }

    static LLMProvider trackingProvider(String summaryText, AtomicInteger callCount) {
        return new LLMProvider() {
            @Override
            public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
                callCount.incrementAndGet();
                return CompletableFuture.completedFuture(new ChatResponse(
                        List.of(new TextBlock(summaryText)),
                        summaryText,
                        "end_turn",
                        Map.of("input_tokens", 100, "output_tokens", 20)
                ));
            }

            @Override
            public void stream(List<Message> messages, ChatOptions options, Consumer<com.aicode.agent.llm.StreamEvent> consumer) {
                throw new UnsupportedOperationException();
            }
        };
    }

    static Message userMsg(String text) {
        return Message.user(text);
    }

    static Message assistantMsg(String text) {
        return new Message("assistant", text);
    }

    static List<Message> buildConversation(int pairs) {
        List<Message> msgs = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            msgs.add(userMsg("Question " + i + ": " + "x".repeat(200)));
            msgs.add(assistantMsg("Answer " + i + ": " + "y".repeat(200)));
        }
        return msgs;
    }

    @Nested
    class TestSummarizeMessages {
        @Test
        void returnsSummary() {
            LLMProvider provider = mockProvider("This is the summary.");
            var msgs = List.of(userMsg("Hello"), assistantMsg("Hi there!"));
            String summary = Compressor.summarizeMessages(provider, msgs, 1024).join();
            assertEquals("This is the summary.", summary);
        }

        @Test
        void formatsToolBlocks() {
            CopyOnWriteArrayList<List<Message>> calls = new CopyOnWriteArrayList<>();
            LLMProvider provider = new LLMProvider() {
                @Override
                public CompletableFuture<ChatResponse> chat(List<Message> messages, ChatOptions options) {
                    calls.add(messages);
                    return CompletableFuture.completedFuture(new ChatResponse(
                            List.of(new TextBlock("Summary with tools.")),
                            "Summary with tools.",
                            "end_turn",
                            Map.of("input_tokens", 100, "output_tokens", 20)
                    ));
                }

                @Override
                public void stream(List<Message> messages, ChatOptions options,
                        Consumer<com.aicode.agent.llm.StreamEvent> consumer) {
                    throw new UnsupportedOperationException();
                }
            };

            var msgs = List.of(
                    Message.assistant(List.of(
                            new ToolUseBlock("1", "read_file", Map.of("file_path", "test.txt")))),
                    Message.userBlocks(List.of(
                            new ToolResultBlock("1", "file contents...")))
            );
            String summary = Compressor.summarizeMessages(provider, msgs, 1024).join();
            assertEquals("Summary with tools.", summary);

            String inputContent = calls.getFirst().getFirst().contentText();
            assertTrue(inputContent.contains("[Tool call: read_file]"));
            assertTrue(inputContent.contains("[Tool result:"));
        }
    }

    @Nested
    class TestCompressConversation {
        @Test
        void noCompressUnderBudget() {
            var config = new Compressor.CompressorConfig(
                    mockProvider("unused"), 100_000, 4, 1024);
            var msgs = List.of(userMsg("Hi"), assistantMsg("Hello"));
            var result = Compressor.compressConversation(config, msgs).join();

            assertFalse(result.compressed());
            assertEquals(2, result.messages().size());
            assertEquals(2, result.originalCount());
            assertEquals(2, result.compressedCount());
            assertEquals(0, result.summaryTokens());
        }

        @Test
        void noCompressFewMessages() {
            var config = new Compressor.CompressorConfig(
                    mockProvider("unused"), 10, 4, 1024);
            var msgs = List.of(userMsg("A"), assistantMsg("B"));
            var result = Compressor.compressConversation(config, msgs).join();
            assertFalse(result.compressed());
        }

        @Test
        void compressOverBudget() {
            LLMProvider provider = mockProvider("Compressed summary.");
            var config = new Compressor.CompressorConfig(provider, 100, 2, 1024);
            var msgs = buildConversation(10);
            var result = Compressor.compressConversation(config, msgs).join();

            assertTrue(result.compressed());
            assertEquals(20, result.originalCount());
            assertEquals(3, result.compressedCount());
            assertTrue(result.summaryTokens() > 0);

            String firstContent = result.messages().getFirst().contentText();
            assertTrue(firstContent.contains("[Previous conversation summary]"));
            assertTrue(firstContent.contains("Compressed summary."));
            assertSame(msgs.getLast(), result.messages().getLast());
        }

        @Test
        void callsProvider() {
            AtomicInteger callCount = new AtomicInteger();
            LLMProvider provider = trackingProvider("sum", callCount);
            var config = new Compressor.CompressorConfig(provider, 10, 2, 1024);
            Compressor.compressConversation(config, buildConversation(5)).join();
            assertEquals(1, callCount.get());
        }
    }

    @Nested
    class TestNeedsCompression {
        @Test
        void shortConversation() {
            var msgs = List.of(userMsg("Hi"), assistantMsg("Hello"));
            assertFalse(Compressor.needsCompression(msgs, 100_000));
        }

        @Test
        void longConversation() {
            assertTrue(Compressor.needsCompression(buildConversation(50), 100));
        }

        @Test
        void empty() {
            assertFalse(Compressor.needsCompression(List.of(), 1000));
        }
    }
}

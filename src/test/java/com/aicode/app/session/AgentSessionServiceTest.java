package com.aicode.app.session;

import com.aicode.agent.Compressor;
import com.aicode.agent.MessageHistory;
import com.aicode.agent.llm.ChatResponse;
import com.aicode.agent.llm.LLMProvider;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.app.application.AgentApplication;
import com.aicode.app.config.AppConfig;
import com.aicode.app.session.ChatTurnDto;
import com.aicode.app.ui.ConversationTranscript;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AgentSessionServiceTest {

    @Test
    void tracksHistoryAndReset() {
        AppConfig config = testConfig();
        AgentApplication application = new AgentApplication(config);
        AgentSessionService service = new AgentSessionService(application, false);
        AgentSession session = service.createSession(config.workspace());

        session.agentHistory().addUser("hello");
        session.agentHistory().addAssistant("hi there");

        assertEquals(2, service.getHistory(session.sessionId()).size());
        assertEquals("user", service.getHistory(session.sessionId()).get(0).role());
        assertEquals("hello", service.getHistory(session.sessionId()).get(0).content());

        service.resetSession(session.sessionId());
        assertTrue(service.getHistory(session.sessionId()).isEmpty());
        assertTrue(service.getHistory(session.sessionId(), ChatMode.CHAT).isEmpty());
    }

    @Test
    void chatAndAgentHistoriesAreSeparate() {
        AppConfig config = testConfig();
        AgentApplication application = new AgentApplication(config);
        AgentSessionService service = new AgentSessionService(application, false);
        AgentSession session = service.createSession(config.workspace());

        session.chatHistory().addUser("chat question");
        session.chatHistory().addAssistant("chat answer");
        session.agentHistory().addUser("agent task");

        assertEquals(2, service.getHistory(session.sessionId(), ChatMode.CHAT).size());
        assertEquals("chat question", service.getHistory(session.sessionId(), ChatMode.CHAT).get(0).content());
        assertEquals(1, service.getHistory(session.sessionId(), ChatMode.AGENT).size());
        assertEquals("agent task", service.getHistory(session.sessionId(), ChatMode.AGENT).get(0).content());
    }

    @Test
    void storesDisplayTextSeparatelyFromPayload() {
        AppConfig config = testConfig();
        AgentSessionService service = new AgentSessionService(new AgentApplication(config), false);
        AgentSession session = service.createSession(config.workspace());

        session.agentHistory().addUser("[@File] full payload", "visible text");
        assertEquals("visible text", service.getHistory(session.sessionId()).getFirst().content());
        assertEquals("visible text", service.getTranscriptTurns(session.sessionId()).getFirst().userText());
    }

    @Test
    void migrateSessionCopiesInMemoryHistory(@TempDir Path tempDir) {
        AppConfig config = testConfig(tempDir);
        String sessionId = java.util.UUID.randomUUID().toString();
        AgentSessionService oldService = new AgentSessionService(new AgentApplication(config), false);
        AgentSession oldSession = oldService.createSession(sessionId, config.workspace());
        oldSession.agentHistory().addUser("payload", "hello");
        oldSession.agentHistory().addAssistant("world");

        AgentSessionService newService = new AgentSessionService(new AgentApplication(config), false);
        ConversationTranscript transcript = new ConversationTranscript();
        transcript.startTurn("hello");
        transcript.appendAssistant("world");

        AgentSession migrated = newService.migrateSession(
                sessionId,
                config.workspace(),
                oldService,
                transcript,
                "测试"
        );

        assertEquals(2, migrated.agentHistory().length());
        assertEquals("hello", newService.getHistory(migrated.sessionId()).getFirst().content());
    }

    @Test
    void recordCompletedTurnAfterCompressionNotice() {
        MessageHistory history = new MessageHistory();
        history.addUser("payload", "hello");
        history.addAssistant("world");

        AgentSession session = new AgentSession(Path.of("/tmp/ws"), null);
        session.appendAgentTurn(ChatTurnDto.standaloneNotice("[Previous conversation summary]\nsummary"));

        // Simulate recordCompletedTurn logic: standalone notice alone should not block recording
        int turnsBefore = 0;
        List<ChatTurnDto> turns = session.agentTurns();
        boolean blocked = false;
        for (int i = turnsBefore; i < turns.size(); i++) {
            if (!turns.get(i).standalone()) {
                blocked = true;
                break;
            }
        }
        assertFalse(blocked);

        ChatTurnDto lastTurn = HistoryTranscriptBuilder.buildLastTurn(history);
        assertNotNull(lastTurn);
        assertEquals("hello", lastTurn.userText());
        session.appendAgentTurn(withTimestamp(lastTurn));
        assertEquals(2, session.agentTurns().size());
        assertFalse(session.agentTurns().getLast().standalone());
    }

    private static ChatTurnDto withTimestamp(ChatTurnDto turn) {
        if (turn.createdAt() != null && !turn.createdAt().isBlank()) {
            return turn;
        }
        return turn.withCreatedAt(java.time.Instant.now().toString());
    }

    @Test
    void appendOnlyTurnsSurviveCompression() {
        MessageHistory history = new MessageHistory();
        for (int i = 0; i < 12; i++) {
            history.addUser("Question " + i + ": " + "x".repeat(500));
            history.addAssistant("Answer " + i + ": " + "y".repeat(500));
        }
        AgentSession session = new AgentSession(Path.of("/tmp/ws"), null);
        for (int i = 0; i < 12; i++) {
            session.appendAgentTurn(ChatTurnDto.userTurn("Question " + i, List.of(), "Answer " + i));
        }
        assertEquals(12, session.agentTurns().size());

        AtomicInteger summarizeCalls = new AtomicInteger();
        var result = Compressor.compressConversation(
                new Compressor.CompressorConfig(trackingProvider("summary text", summarizeCalls), 100, 2, 1024),
                history.getMessages()
        ).join();

        assertTrue(result.compressed());
        history.replaceAll(result.messages());
        session.appendAgentTurn(ChatTurnDto.standaloneNotice("[Previous conversation summary]\nsummary text"));

        assertEquals(13, session.agentTurns().size());
        assertTrue(history.length() < 24);
    }

    @Test
    void compressionWriteBackShrinksHistory() {
        MessageHistory history = new MessageHistory();
        for (int i = 0; i < 12; i++) {
            history.addUser("Question " + i + ": " + "x".repeat(500));
            history.addAssistant("Answer " + i + ": " + "y".repeat(500));
        }
        int before = history.length();
        AtomicInteger summarizeCalls = new AtomicInteger();
        var result = Compressor.compressConversation(
                new Compressor.CompressorConfig(trackingProvider("summary", summarizeCalls), 100, 2, 1024),
                history.getMessages()
        ).join();

        assertTrue(result.compressed());
        history.replaceAll(result.messages());

        assertEquals(1, summarizeCalls.get());
        assertTrue(history.length() < before);
        assertTrue(history.getMessages().getFirst().contentText().contains("[Previous conversation summary]"));
    }

    private static LLMProvider trackingProvider(String summaryText, AtomicInteger callCount) {
        return new LLMProvider() {
            @Override
            public CompletableFuture<com.aicode.agent.llm.ChatResponse> chat(
                    List<Message> messages,
                    com.aicode.agent.llm.ChatOptions options
            ) {
                callCount.incrementAndGet();
                if (messages.stream().anyMatch(m -> m.isStringContent()
                        && m.contentText().contains("Summarize this conversation"))) {
                    return CompletableFuture.completedFuture(new ChatResponse(
                            List.of(new TextBlock(summaryText)),
                            summaryText,
                            "end_turn",
                            Map.of("input_tokens", 1, "output_tokens", 1)
                    ));
                }
                return CompletableFuture.completedFuture(new ChatResponse(
                        List.of(new TextBlock("done")),
                        "done",
                        "end_turn",
                        Map.of("input_tokens", 1, "output_tokens", 1)
                ));
            }

            @Override
            public void stream(
                    List<Message> messages,
                    com.aicode.agent.llm.ChatOptions options,
                    Consumer<com.aicode.agent.llm.StreamEvent> consumer
            ) {
                consumer.accept(new com.aicode.agent.llm.StreamEvent("text_delta", "done"));
                consumer.accept(new com.aicode.agent.llm.StreamEvent(
                        "message_stop",
                        "done",
                        new ChatResponse(
                                List.of(new TextBlock("done")),
                                "done",
                                "end_turn",
                                Map.of("input_tokens", 1, "output_tokens", 1)
                        )
                ));
            }
        };
    }

    private static AppConfig testConfig() {
        return testConfig(Path.of(System.getProperty("user.dir")));
    }

    private static AppConfig testConfig(Path workspace) {
        return new AppConfig(
                "test-api-key",
                "https://api.deepseek.com",
                "deepseek-chat",
                "openai-compatible",
                "Test Agent",
                "🧪",
                workspace,
                8765,
                50,
                32768,
                8192,
                32768,
                2,
                true,
                ""
        );
    }
}

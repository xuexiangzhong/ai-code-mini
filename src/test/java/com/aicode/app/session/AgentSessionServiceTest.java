package com.aicode.app.session;

import com.aicode.app.application.AgentApplication;
import com.aicode.app.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

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

    private static AppConfig testConfig() {
        return new AppConfig(
                "test-api-key",
                "https://api.deepseek.com",
                "deepseek-chat",
                "openai-compatible",
                "Test Agent",
                "🧪",
                Path.of(System.getProperty("user.dir")),
                8765,
                50,
                32768,
                8192,
                32768,
                2,
                true
        );
    }
}

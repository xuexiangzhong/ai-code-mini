package com.aicode.app.application;

import com.aicode.app.approval.ApprovalGate;
import com.aicode.app.config.AppConfig;
import com.aicode.app.event.AgentEventListener;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentApplicationTest {

    @Test
    void buildsApplicationWithToolsAndPrompt() {
        AppConfig config = testConfig();
        AgentApplication app = new AgentApplication(config);

        assertEquals(15, app.tools().size());
        assertTrue(app.tools().stream().anyMatch(t -> "semantic_search".equals(t.name())));
        assertTrue(app.tools().stream().anyMatch(t -> "search_replace".equals(t.name())));
        assertTrue(app.tools().stream().anyMatch(t -> "list_dir".equals(t.name())));
        assertTrue(app.tools().stream().anyMatch(t -> "delete_file".equals(t.name())));
        assertNotNull(app.provider());
        assertFalse(app.systemPrompt().isBlank());
        assertFalse(app.chatSystemPrompt().isBlank());
        assertTrue(app.systemPrompt().contains("Tool Strategy"));
        assertTrue(app.chatSystemPrompt().contains("@ attachments"));
        assertTrue(app.chatOptions().tools().isEmpty());
        ApprovalGate gate = new ApprovalGate(AgentEventListener.NOOP);
        assertNotNull(app.createToolExecutor(gate, AgentEventListener.NOOP));
        assertNotNull(app.toAgentConfig(app.createToolExecutor(gate, AgentEventListener.NOOP)));
    }

    @Test
    void sandboxBlocksOutsideWorkspace() {
        AppConfig config = testConfig();
        AgentApplication app = new AgentApplication(config);

        assertNotNull(app.sandbox().check("/etc/passwd"));
        assertTrue(app.sandbox().isAllowed(config.workspace().resolve("pom.xml").toString()));
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

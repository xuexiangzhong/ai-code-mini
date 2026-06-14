package com.aicode.app.session;

import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SessionPersistenceTest {
    @TempDir
    Path tempHome;

    private static SessionPersistence.StoredSessionSummary summary(
            String sessionId,
            String title,
            Path workspace,
            String updatedAt,
            String legacySummary,
            String agentSummary,
            String chatSummary,
            int agentTurns,
            int chatTurns
    ) {
        return new SessionPersistence.StoredSessionSummary(
                sessionId,
                title,
                workspace.toString(),
                updatedAt,
                legacySummary,
                agentSummary,
                chatSummary,
                agentTurns,
                chatTurns
        );
    }

    @Test
    void saveSplitAndLoadSummaries() throws Exception {
        SessionPersistence store = new SessionPersistence(tempHome.resolve("sessions"));
        Path workspace = tempHome.resolve("project");
        store.createEmpty(workspace, "sess-1", "测试对话");
        List<ChatTurnDto> agentTurns = List.of(
                ChatTurnDto.userTurn("hello", List.of(), "hi", "2026-06-11T10:00:00Z")
        );
        store.saveSplit(
                summary("sess-1", "测试对话", workspace, "2026-06-11T00:00:00Z", null, null, null, 1, 0),
                new SessionPersistence.StoredSessionHistory(
                        List.of(new SessionPersistence.StoredMessage("user", "hello")),
                        List.of(),
                        agentTurns,
                        List.of()
                )
        );

        List<SessionPersistence.StoredSessionSummary> summaries = store.loadSummaries(workspace);
        assertEquals(1, summaries.size());
        assertEquals("sess-1", summaries.getFirst().sessionId());
        SessionPersistence.StoredSessionHistory loaded = store.loadHistory(workspace, "sess-1");
        assertEquals("2026-06-11T10:00:00Z", loaded.agentTurns().getFirst().createdAt());
        assertTrue(Files.isRegularFile(tempHome.resolve("sessions")
                .resolve(SessionPersistence.workspaceKey(workspace.toString()))
                .resolve("sess-1")
                .resolve("summary.json")));
    }

    @Test
    void persistsCompressedSummaries() throws Exception {
        SessionPersistence store = new SessionPersistence(tempHome.resolve("sessions"));
        Path workspace = tempHome.resolve("project");
        store.createEmpty(workspace, "sess-compress", "压缩");
        store.saveSplit(
                summary(
                        "sess-compress",
                        "压缩",
                        workspace,
                        "2026-06-11T00:00:00Z",
                        "legacy summary",
                        "agent summary",
                        "chat summary",
                        2,
                        1
                ),
                new SessionPersistence.StoredSessionHistory(
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        SessionPersistence.StoredSessionSummary loaded = store.loadSummary(workspace, "sess-compress");
        assertEquals("agent summary", loaded.effectiveAgentCompressedSummary());
        assertEquals("chat summary", loaded.effectiveChatCompressedSummary());
    }

    @Test
    void loadTurnPageReturnsRecentSlice() throws Exception {
        SessionPersistence store = new SessionPersistence(tempHome.resolve("sessions"));
        Path workspace = tempHome.resolve("project");
        List<ChatTurnDto> turns = List.of(
                new ChatTurnDto("t1", List.of(), "a1"),
                new ChatTurnDto("t2", List.of(), "a2"),
                new ChatTurnDto("t3", List.of(), "a3"),
                new ChatTurnDto("t4", List.of(), "a4"),
                new ChatTurnDto("t5", List.of(), "a5")
        );
        store.createEmpty(workspace, "sess-page", "分页");
        store.saveSplit(
                summary("sess-page", "分页", workspace, "2026-06-11T00:00:00Z", null, null, null, turns.size(), 0),
                new SessionPersistence.StoredSessionHistory(List.of(), List.of(), turns, List.of())
        );

        SessionPersistence.HistoryPage recent = store.loadTurnPage(
                workspace, "sess-page", ChatMode.AGENT, 2, -1);
        assertEquals(2, recent.turns().size());
        assertEquals("t4", recent.turns().get(0).userText());
        assertEquals("t5", recent.turns().get(1).userText());
        assertTrue(recent.hasOlder());

        SessionPersistence.HistoryPage older = store.loadTurnPage(
                workspace, "sess-page", ChatMode.AGENT, 2, recent.startIndex());
        assertEquals(2, older.turns().size());
        assertEquals("t2", older.turns().get(0).userText());
        assertEquals("t3", older.turns().get(1).userText());
    }

    @Test
    void roundTripsToolBlocksAndDisplayContent() throws Exception {
        SessionPersistence store = new SessionPersistence(tempHome.resolve("sessions"));
        Path workspace = tempHome.resolve("project");
        Message assistant = Message.assistant(List.of(
                new ToolUseBlock("1", "read_file", Map.of("file_path", "Main.java"))
        ));
        SessionPersistence.StoredMessage storedAssistant = SessionMessageCodec.toStored(assistant, null);
        SessionPersistence.StoredMessage storedUser = SessionMessageCodec.toStored(
                Message.user("[@File]\nfull prompt"),
                "short prompt"
        );

        store.createEmpty(workspace, "sess-2", "工具对话");
        store.saveSplit(
                summary("sess-2", "工具对话", workspace, "2026-06-11T00:00:00Z", null, null, null, 1, 0),
                new SessionPersistence.StoredSessionHistory(
                        List.of(storedUser, storedAssistant),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        SessionPersistence.StoredSessionHistory loaded =
                store.loadHistory(workspace, "sess-2");
        Message restoredAssistant = SessionMessageCodec.fromStored(loaded.agentMessages().get(1));
        assertFalse(restoredAssistant.isStringContent());
        assertInstanceOf(ToolUseBlock.class, restoredAssistant.contentBlocks().getFirst());

        SessionPersistence.StoredMessage restoredUser = loaded.agentMessages().getFirst();
        assertEquals("short prompt", SessionMessageCodec.displayContent(restoredUser));
        assertEquals("[@File]\nfull prompt", SessionMessageCodec.fromStored(restoredUser).contentText());
    }

    @Test
    void createEmptyAndLoadSummaries() throws Exception {
        SessionPersistence store = new SessionPersistence(tempHome.resolve("sessions"));
        Path workspace = tempHome.resolve("project");
        store.createEmpty(workspace, "empty-sess", "空会话");

        List<SessionPersistence.StoredSessionSummary> summaries = store.loadSummaries(workspace);
        assertEquals(1, summaries.size());
        assertEquals("empty-sess", summaries.getFirst().sessionId());
        assertEquals("空会话", summaries.getFirst().title());
        assertEquals(0, summaries.getFirst().totalAgentTurns());

        SessionPersistence.StoredSessionHistory history = store.loadHistory(workspace, "empty-sess");
        assertTrue(history.agentMessages().isEmpty());
        assertTrue(history.chatMessages().isEmpty());
    }

    @Test
    void loadAllIncludesEmptySessions() throws Exception {
        SessionPersistence store = new SessionPersistence(tempHome.resolve("sessions"));
        Path workspace = tempHome.resolve("project");
        store.createEmpty(workspace, "empty", "空");
        store.createEmpty(workspace, "filled", "有内容");
        store.saveSplit(
                summary("filled", "有内容", workspace, "2026-06-11T01:00:00Z", null, null, null, 1, 0),
                new SessionPersistence.StoredSessionHistory(
                        List.of(new SessionPersistence.StoredMessage("user", "hello")),
                        List.of(),
                        List.of(new ChatTurnDto("hello", List.of(), "world")),
                        List.of()
                )
        );

        List<SessionPersistence.StoredSession> loaded = store.loadAll(workspace);
        assertEquals(2, loaded.size());
        assertTrue(loaded.stream().anyMatch(s -> s.sessionId().equals("empty")));
        assertTrue(loaded.stream().anyMatch(s -> s.sessionId().equals("filled")));
    }

    @Test
    void saveSplitRequiresExistingSessionDirectory() {
        SessionPersistence store = new SessionPersistence(tempHome.resolve("sessions"));
        Path workspace = tempHome.resolve("project");
        assertThrows(IOException.class, () -> store.saveSplit(
                summary("missing", "无目录", workspace, "2026-06-11T00:00:00Z", null, null, null, 0, 0),
                new SessionPersistence.StoredSessionHistory(List.of(), List.of(), List.of(), List.of())
        ));
    }

    @Test
    void workspaceKeyUsesNormalizedPath() {
        String a = SessionPersistence.workspaceKey("/tmp/project");
        String b = SessionPersistence.workspaceKey("/tmp/project/");
        assertEquals(a, b);
    }
}

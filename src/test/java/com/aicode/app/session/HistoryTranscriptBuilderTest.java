package com.aicode.app.session;

import com.aicode.agent.MessageHistory;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.ToolUseBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HistoryTranscriptBuilderTest {
    @Test
    void rebuildsTurnWithToolActivities() {
        MessageHistory history = new MessageHistory();
        history.addUser("fix bug", "fix bug");
        history.addAssistantBlocks(List.of(
                new ToolUseBlock("1", "read_file", Map.of("file_path", "Main.java"))
        ));
        history.addMessage(Message.userBlocks(List.of(
                new com.aicode.agent.llm.ToolResultBlock("1", "class Main {}")
        )));
        history.addAssistant("fixed");

        List<ChatTurnDto> turns = HistoryTranscriptBuilder.buildTurns(history);
        assertEquals(1, turns.size());
        assertEquals("fix bug", turns.getFirst().userText());
        assertEquals("fixed", turns.getFirst().assistantText());
        assertTrue(turns.getFirst().activities().getFirst().contains("read_file"));
    }
}

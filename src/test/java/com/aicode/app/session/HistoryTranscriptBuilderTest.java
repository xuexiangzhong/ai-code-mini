package com.aicode.app.session;

import com.aicode.agent.MessageHistory;
import com.aicode.agent.llm.ImageBlock;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.ToolUseBlock;
import com.aicode.app.session.UserMessagePayload;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
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

    @Test
    void rebuildsTurnWithUserImages() {
        Path image = Path.of("/tmp/screenshot.png");
        MessageHistory history = new MessageHistory();
        history.addUserPayload(UserMessagePayload.blocks(
                List.of(new TextBlock("看看这张图"), ImageBlock.of(image, new byte[]{1, 2, 3})),
                "看看这张图"
        ));
        history.addAssistant("图片里是一个界面");

        List<ChatTurnDto> turns = HistoryTranscriptBuilder.buildTurns(history);
        assertEquals(1, turns.size());
        assertEquals("看看这张图", turns.getFirst().userText());
        assertEquals(1, turns.getFirst().userImagePaths().size());
        assertTrue(turns.getFirst().userImagePaths().getFirst().endsWith("screenshot.png"));
    }
}

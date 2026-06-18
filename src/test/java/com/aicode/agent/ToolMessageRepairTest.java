package com.aicode.agent;

import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.LLMHelpers;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolMessageRepairTest {
    @Test
    void insertsMissingToolResultsBeforeNextUserTurn() {
        Message assistant = Message.assistant(List.of(
                new ToolUseBlock("call_1", "read_file", Map.of("file_path", "a.txt"))
        ));
        List<Message> broken = List.of(
                Message.user("fix bug"),
                assistant,
                Message.user("also check tests")
        );

        List<Message> repaired = ToolMessageRepair.repair(broken);

        assertEquals(4, repaired.size());
        assertInstanceOf(ToolResultBlock.class, repaired.get(2).contentBlocks().getFirst());
        assertEquals("call_1", ((ToolResultBlock) repaired.get(2).contentBlocks().getFirst()).toolUseId());
        assertEquals("also check tests", repaired.get(3).contentText());
    }

    @Test
    void fillsMissingParallelToolResults() {
        Message assistant = Message.assistant(List.of(
                new ToolUseBlock("call_1", "read_file", Map.of()),
                new ToolUseBlock("call_2", "grep", Map.of())
        ));
        Message partial = Message.userBlocks(List.of(
                LLMHelpers.createToolResult("call_1", "ok", false)
        ));
        List<Message> repaired = ToolMessageRepair.repair(List.of(Message.user("go"), assistant, partial));

        ToolResultBlock second = (ToolResultBlock) repaired.get(2).contentBlocks().get(1);
        assertEquals("call_2", second.toolUseId());
        assertTrue(second.isError());
    }

    @Test
    void finalizeCancelledToolTurnKeepsCompletedResults() {
        List<Message> messages = new java.util.ArrayList<>(List.of(
                Message.user("go"),
                Message.assistant(List.of(
                        new ToolUseBlock("call_1", "read_file", Map.of()),
                        new ToolUseBlock("call_2", "grep", Map.of())
                ))
        ));
        List<Agent.ToolCallRecord> toolCalls = new java.util.ArrayList<>(List.of(
                new Agent.ToolCallRecord("read_file", Map.of(), "file ok")
        ));

        ToolMessageRepair.finalizeCancelledToolTurn(messages, toolCalls, 0);

        assertEquals(3, messages.size());
        List<ContentBlock> blocks = messages.get(2).contentBlocks();
        assertEquals(2, blocks.size());
        ToolResultBlock first = (ToolResultBlock) blocks.get(0);
        ToolResultBlock second = (ToolResultBlock) blocks.get(1);
        assertEquals("call_1", first.toolUseId());
        assertEquals("file ok", first.content());
        assertFalse(first.isError());
        assertEquals("call_2", second.toolUseId());
        assertTrue(second.isError());
    }

    @Test
    void finalizeCancelledToolTurnAppendsWhenPending() {
        List<Message> messages = new java.util.ArrayList<>(List.of(
                Message.user("go"),
                Message.assistant(List.of(new ToolUseBlock("call_1", "bash", Map.of())))
        ));

        ToolMessageRepair.finalizeCancelledToolTurn(messages, List.of(), 0);

        assertEquals(3, messages.size());
        assertTrue(ToolMessageRepair.isToolResultUser(messages.get(2)));
    }

    @Test
    void differsDetectsContentChangeWithoutSizeChange() {
        Message assistant = Message.assistant(List.of(
                new ToolUseBlock("call_1", "read_file", Map.of()),
                new ToolUseBlock("call_2", "grep", Map.of())
        ));
        Message partial = Message.userBlocks(List.of(
                LLMHelpers.createToolResult("call_1", "ok", false)
        ));
        List<Message> broken = List.of(Message.user("go"), assistant, partial);
        List<Message> repaired = ToolMessageRepair.repair(broken);

        assertEquals(broken.size(), repaired.size());
        assertTrue(ToolMessageRepair.differs(broken, repaired));
    }
}

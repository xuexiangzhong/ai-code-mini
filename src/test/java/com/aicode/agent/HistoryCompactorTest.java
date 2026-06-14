package com.aicode.agent;

import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HistoryCompactorTest {
    @Test
    void compactsOldToolResultsButKeepsRecentTurns() {
        List<Message> messages = List.of(
                Message.user("first question"),
                Message.assistant(List.of(new ToolUseBlock("t1", "read_file", Map.of()))),
                Message.userBlocks(List.of(new ToolResultBlock("t1", "x".repeat(2000)))),
                new Message("assistant", "done once"),
                Message.user("second question"),
                new Message("assistant", "answer two")
        );

        HistoryCompactor.CompactResult result = HistoryCompactor.compact(messages, 500);
        assertTrue(result.changed());
        assertTrue(result.tokensAfter() < result.tokensBefore());

        Message oldToolResult = result.messages().get(2);
        String content = ((ToolResultBlock) oldToolResult.contentBlocks().getFirst()).content();
        assertTrue(content.contains("历史 tool 输出已截断"));
        assertEquals("answer two", result.messages().get(5).contentText());
    }

    @Test
    void stripsOldAttachmentBlocksFromUserMessages() {
        String payload = "[@文件: Foo.java]\n<untrusted_context source=\"Foo.java\">\n"
                + "class Foo {}\n</untrusted_context>\n\n真正的问题";
        List<Message> messages = List.of(
                Message.user(payload),
                new Message("assistant", "ok"),
                Message.user("follow up"),
                new Message("assistant", "sure")
        );

        HistoryCompactor.CompactResult result = HistoryCompactor.compact(messages, 50);
        String oldUser = result.messages().getFirst().contentText();
        assertFalse(oldUser.contains("class Foo"));
        assertTrue(oldUser.contains("历史附件已省略"));
        assertTrue(oldUser.contains("真正的问题"));
    }

    @Test
    void skipsCompactionUnderSoftThreshold() {
        List<Message> messages = List.of(
                Message.user("hi"),
                new Message("assistant", "hello")
        );
        HistoryCompactor.CompactResult result = HistoryCompactor.compact(messages, 100_000);
        assertFalse(result.changed());
    }

    @Test
    void preservesSummaryMessage() {
        List<Message> messages = List.of(
                Message.user("[Previous conversation summary]\nDid X and Y"),
                Message.user("new question"),
                new Message("assistant", "answer")
        );
        HistoryCompactor.CompactResult result = HistoryCompactor.compact(messages, 100);
        assertEquals("[Previous conversation summary]\nDid X and Y", result.messages().getFirst().contentText());
    }
}

package com.aicode.agent;

import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageHistoryTest {
    @Test
    void replaceAllPreservesDisplayTextByIndex() {
        MessageHistory history = new MessageHistory();
        history.addUser("[@File] full payload", "visible text");
        history.addAssistant("answer");

        history.replaceAll(history.getMessages());

        assertEquals("visible text", history.userDisplayText(0));
    }

    @Test
    void replaceAllPreservesDisplayTextByContentMatch() {
        MessageHistory history = new MessageHistory();
        history.addUser("Question one", "short one");
        history.addAssistant("Answer one");
        history.addUser("Question two", "short two");
        history.addAssistant("Answer two");

        List<Message> compressed = List.of(
                Message.user("[Previous conversation summary]\nsummary"),
                Message.user("Question two"),
                new Message("assistant", "Answer two")
        );
        history.replaceAll(compressed);

        assertEquals("short two", history.userDisplayText(1));
    }
}

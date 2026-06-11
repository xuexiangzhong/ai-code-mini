package com.aicode.agent;

import com.aicode.agent.llm.Message;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HistoryTest {
    @Nested
    class TestMessageHistory {
        @Test
        void addUserAndAssistantMessages() {
            MessageHistory history = new MessageHistory();
            history.addUser("Hello");
            history.addAssistant("Hi there!");

            var msgs = history.getMessages();
            assertEquals(2, msgs.size());
            assertEquals(Message.user("Hello"), msgs.get(0));
            assertEquals(new Message("assistant", "Hi there!"), msgs.get(1));
        }

        @Test
        void getMessagesReturnsCopy() {
            MessageHistory history = new MessageHistory();
            history.addUser("Hello");
            var msgs = new java.util.ArrayList<>(history.getMessages());
            msgs.add(Message.user("injected"));
            assertEquals(1, history.getMessages().size());
        }

        @Test
        void getLastN() {
            MessageHistory history = new MessageHistory();
            history.addUser("1");
            history.addAssistant("2");
            history.addUser("3");

            var last2 = history.getLastN(2);
            assertEquals(2, last2.size());
            assertEquals("2", last2.get(0).contentText());
            assertEquals("3", last2.get(1).contentText());
        }

        @Test
        void getLastNExceedsLength() {
            MessageHistory history = new MessageHistory();
            history.addUser("only");
            assertEquals(1, history.getLastN(10).size());
        }

        @Test
        void length() {
            MessageHistory history = new MessageHistory();
            assertEquals(0, history.length());
            history.addUser("1");
            history.addAssistant("2");
            assertEquals(2, history.length());
        }

        @Test
        void clear() {
            MessageHistory history = new MessageHistory();
            history.addUser("1");
            history.addAssistant("2");
            history.clear();
            assertEquals(0, history.length());
            assertTrue(history.getMessages().isEmpty());
        }

        @Test
        void getLastMessage() {
            MessageHistory history = new MessageHistory();
            assertNull(history.getLastMessage());
            history.addUser("Hello");
            history.addAssistant("Hi");
            assertEquals(new Message("assistant", "Hi"), history.getLastMessage());
        }

        @Test
        void removeLast() {
            MessageHistory history = new MessageHistory();
            history.addUser("Hello");
            history.addAssistant("Hi");
            Message removed = history.removeLast();
            assertEquals(new Message("assistant", "Hi"), removed);
            assertEquals(1, history.length());
        }

        @Test
        void removeLastEmpty() {
            MessageHistory history = new MessageHistory();
            assertNull(history.removeLast());
        }

        @Test
        void conversationAlternation() {
            MessageHistory history = new MessageHistory();
            history.addUser("Q1");
            history.addAssistant("A1");
            history.addUser("Q2");
            history.addAssistant("A2");

            var msgs = history.getMessages();
            var roles = msgs.stream().map(Message::role).toList();
            assertEquals(List.of("user", "assistant", "user", "assistant"), roles);
        }
    }
}

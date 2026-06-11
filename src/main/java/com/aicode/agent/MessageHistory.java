package com.aicode.agent;

import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages conversation history between user and assistant.
 */
public class MessageHistory {
    private final List<Message> messages = new ArrayList<>();

    public void addUser(String content) {
        messages.add(Message.user(content));
    }

    public void addAssistant(String content) {
        messages.add(new Message("assistant", content));
    }

    public void addAssistantBlocks(List<ContentBlock> blocks) {
        messages.add(Message.assistant(new ArrayList<>(blocks)));
    }

    public void addMessage(Message message) {
        messages.add(message);
    }

    public List<Message> getMessages() {
        return List.copyOf(messages);
    }

    public List<Message> getLastN(int n) {
        if (n >= messages.size()) {
            return List.copyOf(messages);
        }
        return List.copyOf(messages.subList(messages.size() - n, messages.size()));
    }

    public int length() {
        return messages.size();
    }

    public void clear() {
        messages.clear();
    }

    public Message getLastMessage() {
        return messages.isEmpty() ? null : messages.getLast();
    }

    public Message removeLast() {
        return messages.isEmpty() ? null : messages.removeLast();
    }
}

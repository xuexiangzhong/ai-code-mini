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
    private final List<String> userDisplayTexts = new ArrayList<>();

    public void addUser(String content) {
        addUser(content, content);
    }

    public void addUser(String content, String displayContent) {
        messages.add(Message.user(content));
        userDisplayTexts.add(displayContent != null ? displayContent : content);
    }

    public void addAssistant(String content) {
        messages.add(new Message("assistant", content));
        userDisplayTexts.add(null);
    }

    public void addAssistantBlocks(List<ContentBlock> blocks) {
        messages.add(Message.assistant(new ArrayList<>(blocks)));
        userDisplayTexts.add(null);
    }

    public void addMessage(Message message) {
        messages.add(message);
        userDisplayTexts.add(null);
    }

    public void replaceAll(List<Message> replacement) {
        List<Message> oldMessages = List.copyOf(messages);
        List<String> oldDisplays = new ArrayList<>(userDisplayTexts);

        messages.clear();
        userDisplayTexts.clear();
        for (int i = 0; i < replacement.size(); i++) {
            Message message = replacement.get(i);
            messages.add(message);
            userDisplayTexts.add(resolveDisplayText(message, i, replacement.size(), oldMessages, oldDisplays));
        }
    }

    private static String resolveDisplayText(
            Message message,
            int index,
            int replacementSize,
            List<Message> oldMessages,
            List<String> oldDisplays
    ) {
        if (!"user".equals(message.role()) || !message.isStringContent()) {
            return null;
        }
        String content = message.contentText();
        if (oldMessages.size() == replacementSize && index < oldDisplays.size()) {
            String display = oldDisplays.get(index);
            Message old = oldMessages.get(index);
            if (display != null
                    && "user".equals(old.role())
                    && old.isStringContent()
                    && content.equals(old.contentText())) {
                return display;
            }
        }
        for (int i = 0; i < oldMessages.size(); i++) {
            Message old = oldMessages.get(i);
            if (i < oldDisplays.size()
                    && oldDisplays.get(i) != null
                    && "user".equals(old.role())
                    && old.isStringContent()
                    && content.equals(old.contentText())) {
                return oldDisplays.get(i);
            }
        }
        return null;
    }

    public List<Message> getMessages() {
        return List.copyOf(messages);
    }

    public List<String> getUserDisplayTexts() {
        return List.copyOf(userDisplayTexts);
    }

    public String userDisplayText(int index) {
        if (index < 0 || index >= messages.size()) {
            return "";
        }
        String display = userDisplayTexts.get(index);
        if (display != null) {
            return display;
        }
        Message message = messages.get(index);
        if ("user".equals(message.role()) && message.isStringContent()) {
            return message.contentText();
        }
        return "";
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
        userDisplayTexts.clear();
    }

    public Message getLastMessage() {
        return messages.isEmpty() ? null : messages.getLast();
    }

    public Message removeLast() {
        if (messages.isEmpty()) {
            return null;
        }
        userDisplayTexts.removeLast();
        return messages.removeLast();
    }
}

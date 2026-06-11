package com.aicode.agent.llm;

import java.util.List;

/**
 * Core message type for LLM conversation.
 * {@code content} is either a {@link String} or a {@link List} of {@link ContentBlock}.
 */
public record Message(String role, Object content) {
    public static Message user(String text) {
        return new Message("user", text);
    }

    public static Message userBlocks(List<ContentBlock> blocks) {
        return new Message("user", blocks);
    }

    public static Message assistant(List<ContentBlock> blocks) {
        return new Message("assistant", blocks);
    }

    public boolean isStringContent() {
        return content instanceof String;
    }

    @SuppressWarnings("unchecked")
    public List<ContentBlock> contentBlocks() {
        if (content instanceof List<?> list) {
            return (List<ContentBlock>) list;
        }
        throw new IllegalStateException("Message content is not a list of blocks");
    }

    public String contentText() {
        if (content instanceof String s) {
            return s;
        }
        throw new IllegalStateException("Message content is not a string");
    }
}

package com.aicode.app.session;

import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.llm.ToolResultBlock;
import com.aicode.agent.llm.ToolUseBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Serialize {@link Message} instances for session persistence. */
public final class SessionMessageCodec {
    private SessionMessageCodec() {}

    public static SessionPersistence.StoredMessage toStored(Message message, String displayContent) {
        if (message.isStringContent()) {
            return new SessionPersistence.StoredMessage(
                    message.role(),
                    message.contentText(),
                    displayContent,
                    List.of()
            );
        }
        List<SessionPersistence.StoredMessage.PersistedBlockRef> blocks = new ArrayList<>();
        for (ContentBlock block : message.contentBlocks()) {
            blocks.add(toBlock(block));
        }
        return new SessionPersistence.StoredMessage(message.role(), null, null, blocks);
    }

    public static Message fromStored(SessionPersistence.StoredMessage stored) {
        if (stored.blocks() != null && !stored.blocks().isEmpty()) {
            List<ContentBlock> blocks = new ArrayList<>();
            for (SessionPersistence.StoredMessage.PersistedBlockRef block : stored.blocks()) {
                blocks.add(fromBlock(block));
            }
            return "user".equals(stored.role()) ? Message.userBlocks(blocks) : Message.assistant(blocks);
        }
        String content = stored.content() != null ? stored.content() : "";
        return "user".equals(stored.role()) ? Message.user(content) : new Message("assistant", content);
    }

    public static String displayContent(SessionPersistence.StoredMessage stored) {
        if (stored.displayContent() != null && !stored.displayContent().isBlank()) {
            return stored.displayContent();
        }
        if (stored.content() != null) {
            return stored.content();
        }
        return "";
    }

    private static SessionPersistence.StoredMessage.PersistedBlockRef toBlock(ContentBlock block) {
        if (block instanceof TextBlock tb) {
            return new SessionPersistence.StoredMessage.PersistedBlockRef(
                    "text", tb.text(), null, null, null, null, null, null
            );
        }
        if (block instanceof ToolUseBlock tub) {
            return new SessionPersistence.StoredMessage.PersistedBlockRef(
                    "tool_use", null, tub.id(), tub.name(), tub.input(), null, null, null
            );
        }
        if (block instanceof ToolResultBlock trb) {
            return new SessionPersistence.StoredMessage.PersistedBlockRef(
                    "tool_result", null, null, null, null, trb.toolUseId(), trb.content(), trb.isError()
            );
        }
        throw new IllegalArgumentException("Unknown block type: " + block.getClass());
    }

    private static ContentBlock fromBlock(SessionPersistence.StoredMessage.PersistedBlockRef block) {
        return switch (block.type()) {
            case "text" -> new TextBlock(block.text() != null ? block.text() : "");
            case "tool_use" -> new ToolUseBlock(
                    block.id() != null ? block.id() : "",
                    block.name() != null ? block.name() : "",
                    block.input() != null ? block.input() : Map.of()
            );
            case "tool_result" -> new ToolResultBlock(
                    block.toolUseId() != null ? block.toolUseId() : "",
                    block.content() != null ? block.content() : "",
                    Boolean.TRUE.equals(block.isError())
            );
            default -> throw new IllegalArgumentException("Unknown persisted block type: " + block.type());
        };
    }
}

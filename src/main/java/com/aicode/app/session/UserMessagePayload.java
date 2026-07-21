package com.aicode.app.session;

import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.ImageBlock;
import com.aicode.agent.llm.Message;
import com.aicode.agent.llm.TextBlock;

import java.util.List;

/** User message assembled from composer text and @ attachments (text and/or images). */
public record UserMessagePayload(Message message, String displayText) {

    public static UserMessagePayload text(String apiText, String displayText) {
        return new UserMessagePayload(Message.user(apiText), displayText);
    }

    public static UserMessagePayload blocks(List<ContentBlock> blocks, String displayText) {
        if (blocks.size() == 1 && blocks.getFirst() instanceof TextBlock tb) {
            return text(tb.text(), displayText);
        }
        return new UserMessagePayload(Message.userBlocks(blocks), displayText);
    }

    public boolean hasImages() {
        return !imagePaths().isEmpty();
    }

    public List<String> imagePaths() {
        if (message.isStringContent()) {
            return List.of();
        }
        return message.contentBlocks().stream()
                .filter(ImageBlock.class::isInstance)
                .map(ImageBlock.class::cast)
                .map(ImageBlock::sourcePath)
                .toList();
    }
}

package com.aicode.app.ui;

import com.aicode.agent.llm.ImageBlock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ChatContextAttachmentsTest {

    @Test
    void buildUserMessageIncludesImageBlocks(@TempDir Path workspace) throws Exception {
        Path image = workspace.resolve("shot.png");
        Files.write(image, new byte[] {(byte) 137, 80, 78, 71});

        ChatContextAttachments attachments = new ChatContextAttachments();
        attachments.addImage(image, Files.readAllBytes(image), ImageBlock.mediaTypeFor(image));

        var payload = attachments.buildUserMessage("what is this?", workspace);
        assertTrue(payload.hasImages());
        assertFalse(payload.message().isStringContent());
        assertEquals(2, payload.message().contentBlocks().size());
        assertInstanceOf(ImageBlock.class, payload.message().contentBlocks().get(1));
    }
}

package com.aicode.app.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileEditRevertorTest {
    @TempDir
    Path dir;

    @Test
    void revertModifiedFile() throws Exception {
        Path file = dir.resolve("a.txt");
        Files.writeString(file, "new");
        FileEditProposal proposal = FileEditProposal.create(file, "old", "new", false);
        FileEditRevertor.revert(proposal);
        assertEquals("old", Files.readString(file));
    }

    @Test
    void revertCreatedFileDeletesIt() throws Exception {
        Path file = dir.resolve("new.txt");
        Files.writeString(file, "content");
        FileEditProposal proposal = FileEditProposal.create(file, null, "content", true);
        FileEditRevertor.revert(proposal);
        assertFalse(Files.exists(file));
    }
}

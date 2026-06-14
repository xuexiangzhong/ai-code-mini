package com.aicode.app.session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FileEditRevertor {
    private FileEditRevertor() {}

    public static void revert(FileEditProposal proposal) throws IOException {
        Path path = proposal.filePath();
        if (proposal.created()) {
            Files.deleteIfExists(path);
            return;
        }
        if (proposal.oldContent() != null) {
            Files.writeString(path, proposal.oldContent());
        }
    }
}

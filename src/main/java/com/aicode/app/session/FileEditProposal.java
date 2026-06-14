package com.aicode.app.session;

import com.aicode.agent.tools.FileDiff;

import java.nio.file.Path;
import java.util.UUID;

/** A file change made by the Agent that the user can accept or revert. */
public record FileEditProposal(
        String id,
        Path filePath,
        String oldContent,
        String newContent,
        boolean created
) {
    public static FileEditProposal create(Path filePath, String oldContent, String newContent, boolean created) {
        return new FileEditProposal(UUID.randomUUID().toString(), filePath, oldContent, newContent, created);
    }

    public String diffText() {
        String before = oldContent != null ? oldContent : "";
        return FileDiff.generate(before, newContent, filePath.toString());
    }

    public String summary() {
        String name = filePath.getFileName() != null ? filePath.getFileName().toString() : filePath.toString();
        return created ? "新建 " + name : "修改 " + name;
    }
}

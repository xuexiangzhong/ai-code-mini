package com.aicode.app.ui;

import javafx.scene.Node;

import java.nio.file.Path;

/** Chat composer with optional @ context attachments. */
public interface ChatComposerInput {
    Node node();

    String getText();

    void clear();

    void disableInput(boolean disabled);

    void setPromptText(String text);

    void requestFocus();

    ChatContextAttachments attachments();

    default Path activeFile() {
        return null;
    }

    void clearAfterSend();

    void setOnSubmit(Runnable onSubmit);
}

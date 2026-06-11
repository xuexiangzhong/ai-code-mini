package com.aicode.app.ui;

import javafx.scene.Node;

/** Chat composer with optional @ context attachments. */
public interface ChatComposerInput {
    Node node();

    String getText();

    void clear();

    void disableInput(boolean disabled);

    void setPromptText(String text);

    void requestFocus();

    ChatContextAttachments attachments();

    void clearAfterSend();

    void setOnSubmit(Runnable onSubmit);
}

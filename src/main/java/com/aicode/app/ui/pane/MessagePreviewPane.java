package com.aicode.app.ui.pane;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

/** Static message shown when a file cannot be previewed in the editor. */
public final class MessagePreviewPane extends StackPane {
    private final Label label = new Label();

    public MessagePreviewPane() {
        label.setWrapText(true);
        label.setMaxWidth(480);
        label.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");
        setStyle("-fx-background-color: #1e1e1e;");
        getChildren().add(label);
        StackPane.setAlignment(label, Pos.CENTER);
    }

    public void showMessage(String message) {
        label.setText(message == null ? "" : message);
    }

    public void clear() {
        label.setText("");
    }
}

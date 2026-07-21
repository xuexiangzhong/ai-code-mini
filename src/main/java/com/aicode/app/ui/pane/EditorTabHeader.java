package com.aicode.app.ui.pane;

import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.util.function.Consumer;

/** Tab caption with optional encoding selector for text files. */
final class EditorTabHeader {
    private final HBox root;
    private final Label nameLabel;
    private final ComboBox<String> encodingBox;
    private boolean suppressEncodingEvent;

    EditorTabHeader(String fileName, Consumer<String> onEncodingChanged) {
        nameLabel = new Label(fileName);
        nameLabel.getStyleClass().add("tab-label");

        encodingBox = new ComboBox<>();
        encodingBox.getItems().addAll(EditorCharsets.choices());
        encodingBox.setValue(EditorCharsets.DEFAULT);
        encodingBox.getStyleClass().add("editor-encoding-box");
        encodingBox.setMaxWidth(96);
        encodingBox.setVisible(false);
        encodingBox.setManaged(false);
        encodingBox.setOnAction(event -> {
            if (suppressEncodingEvent) {
                return;
            }
            String selected = encodingBox.getValue();
            if (selected != null) {
                onEncodingChanged.accept(selected);
            }
        });

        root = new HBox(6, nameLabel, encodingBox);
        root.setAlignment(Pos.CENTER_LEFT);
    }

    void setFileName(String fileName, boolean dirty) {
        nameLabel.setText(fileName + (dirty ? " *" : ""));
    }

    void setEncodingVisible(boolean visible) {
        encodingBox.setVisible(visible);
        encodingBox.setManaged(visible);
    }

    void setEncoding(String charsetName) {
        suppressEncodingEvent = true;
        encodingBox.setValue(EditorCharsets.normalize(charsetName));
        suppressEncodingEvent = false;
    }

    HBox node() {
        return root;
    }
}

package com.aicode.app.ui.pane;

import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

import java.io.ByteArrayInputStream;

/** Read-only image preview for editor tabs. */
public final class ImagePreviewPane extends StackPane {
    private final ImageView imageView = new ImageView();

    public ImagePreviewPane() {
        ScrollPane scrollPane = new ScrollPane(imageView);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: #1e1e1e;");
        imageView.setPreserveRatio(true);
        setStyle("-fx-background-color: #1e1e1e;");
        getChildren().add(scrollPane);
    }

    public void showImage(byte[] bytes) {
        imageView.setImage(new Image(new ByteArrayInputStream(bytes)));
    }

    public void clear() {
        imageView.setImage(null);
    }
}

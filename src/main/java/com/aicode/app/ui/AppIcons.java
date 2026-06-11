package com.aicode.app.ui;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

/** Loads bundled app icons for JavaFX windows. */
public final class AppIcons {
    private AppIcons() {}

    public static void apply(Stage stage) {
        load("/icons/aicode-256.png").ifPresent(img -> stage.getIcons().add(img));
        load("/icons/aicode-32.png").ifPresent(img -> stage.getIcons().add(img));
    }

    private static java.util.Optional<Image> load(String resource) {
        var url = AppIcons.class.getResource(resource);
        if (url == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Image(Objects.requireNonNull(url).toExternalForm(), true));
    }
}

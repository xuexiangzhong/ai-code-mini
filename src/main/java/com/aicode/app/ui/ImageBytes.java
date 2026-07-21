package com.aicode.app.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Converts JavaFX {@link Image} to PNG bytes for LLM multimodal payloads. */
public final class ImageBytes {
    private ImageBytes() {}

    public static byte[] toPng(Image image) throws IOException {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            throw new IOException("Empty image");
        }
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        WritableImage copy = new WritableImage(width, height);
        copy.getPixelWriter().setPixels(0, 0, width, height, image.getPixelReader(), 0, 0);

        PixelReader reader = copy.getPixelReader();
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(buffered, "png", out)) {
            throw new IOException("Failed to encode PNG");
        }
        return out.toByteArray();
    }
}

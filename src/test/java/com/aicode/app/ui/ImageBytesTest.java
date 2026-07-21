package com.aicode.app.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageBytesTest {

    @Test
    void encodesFxImageAsPng() throws Exception {
        WritableImage image = new WritableImage(2, 2);
        PixelWriter writer = image.getPixelWriter();
        writer.setArgb(0, 0, 0xFF_FF_00_00);
        writer.setArgb(1, 1, 0xFF_00_FF_00);

        byte[] png = ImageBytes.toPng(image);
        assertTrue(png.length > 8);
        assertEquals((byte) 137, png[0]);
        assertEquals((byte) 'P', png[1]);
        assertEquals((byte) 'N', png[2]);
        assertEquals((byte) 'G', png[3]);
    }

    @Test
    void rejectsEmptyImage() {
        assertThrows(Exception.class, () -> ImageBytes.toPng(new Image("data:,")));
    }
}

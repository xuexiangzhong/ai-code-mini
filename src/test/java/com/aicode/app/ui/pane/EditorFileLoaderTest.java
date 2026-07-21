package com.aicode.app.ui.pane;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EditorFileLoaderTest {
    @Test
    void loadsPngAsImage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("icon.png");
        Files.write(file, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

        EditorFileContent loaded = EditorFileLoader.load(file);

        assertEquals(EditorViewMode.IMAGE, loaded.mode());
        assertNotNull(loaded.imageBytes());
        assertEquals(8, loaded.imageBytes().length);
    }

    @Test
    void loadsJpgAsImage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("photo.jpg");
        Files.write(file, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});

        EditorFileContent loaded = EditorFileLoader.load(file);

        assertEquals(EditorViewMode.IMAGE, loaded.mode());
    }

    @Test
    void loadsClassAsHex(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("App.class");
        Files.write(file, new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE});

        EditorFileContent loaded = EditorFileLoader.load(file);

        assertEquals(EditorViewMode.HEX, loaded.mode());
        assertTrue(loaded.text().startsWith("00000000  CA FE BA BE"));
        assertFalse(loaded.editable());
    }

    @Test
    void extensionlessFileDefaultsToUtf8(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("Makefile");
        Files.writeString(file, "hello\n世界");

        EditorFileContent loaded = EditorFileLoader.load(file);

        assertEquals(EditorViewMode.TEXT, loaded.mode());
        assertEquals("hello\n世界", loaded.text());
        assertEquals(EditorCharsets.DEFAULT, loaded.charsetName());
        assertTrue(loaded.editable());
    }

    @Test
    void largeExtensionlessFileShowsMessage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("blob");
        Files.write(file, new byte[(int) EditorFileLoader.MAX_EXTENSIONLESS_BYTES + 1]);

        EditorFileContent loaded = EditorFileLoader.load(file);

        assertEquals(EditorViewMode.MESSAGE, loaded.mode());
        assertTrue(loaded.text().contains("不支持预览"));
    }

    @Test
    void unknownExtensionDefaultsToUtf8(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config.env");
        Files.writeString(file, "KEY=value\n");

        EditorFileContent loaded = EditorFileLoader.load(file);

        assertEquals(EditorViewMode.TEXT, loaded.mode());
        assertEquals("KEY=value\n", loaded.text());
        assertEquals(EditorCharsets.DEFAULT, loaded.charsetName());
    }

    @Test
    void redecodeUsesSelectedCharset(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("gbk.txt");
        byte[] raw = "中文".getBytes(Charset.forName("GBK"));
        Files.write(file, raw);

        EditorFileContent utf8View = EditorFileLoader.load(file);
        assertNotEquals("中文", utf8View.text());

        EditorFileContent gbkView = EditorFileLoader.redecode(raw, "GBK");
        assertEquals("中文", gbkView.text());
        assertEquals("GBK", gbkView.charsetName());
    }

    @Test
    void largeUnknownExtensionShowsMessage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("data.evn");
        Files.write(file, new byte[(int) EditorFileLoader.MAX_EXTENSIONLESS_BYTES + 1]);

        EditorFileContent loaded = EditorFileLoader.load(file);

        assertEquals(EditorViewMode.MESSAGE, loaded.mode());
        assertTrue(loaded.text().contains("不支持预览"));
    }

    @Test
    void utf8TextFileStillLoads(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("notes.txt");
        Files.writeString(file, "hello\n世界");

        EditorFileContent loaded = EditorFileLoader.load(file);

        assertEquals(EditorViewMode.TEXT, loaded.mode());
        assertEquals("hello\n世界", loaded.text());
        assertEquals(EditorCharsets.DEFAULT, loaded.charsetName());
    }

    @Test
    void extensionHelperTreatsLeadingDotAsExtensionless() {
        assertNull(EditorFileLoader.extension(".gitignore"));
        assertEquals("png", EditorFileLoader.extension("icon.png"));
    }
}

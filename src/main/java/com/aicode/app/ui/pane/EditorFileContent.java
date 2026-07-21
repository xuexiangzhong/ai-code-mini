package com.aicode.app.ui.pane;

import java.util.Arrays;

/** Loaded editor payload for a file path. */
public final class EditorFileContent {
    private final EditorViewMode mode;
    private final String text;
    private final byte[] imageBytes;
    private final byte[] rawBytes;
    private final String charsetName;

    private EditorFileContent(
            EditorViewMode mode,
            String text,
            byte[] imageBytes,
            byte[] rawBytes,
            String charsetName
    ) {
        this.mode = mode;
        this.text = text;
        this.imageBytes = imageBytes == null ? null : Arrays.copyOf(imageBytes, imageBytes.length);
        this.rawBytes = rawBytes == null ? null : Arrays.copyOf(rawBytes, rawBytes.length);
        this.charsetName = charsetName;
    }

    public static EditorFileContent text(String content, byte[] rawBytes, String charsetName) {
        return new EditorFileContent(
                EditorViewMode.TEXT,
                content,
                null,
                rawBytes,
                EditorCharsets.normalize(charsetName)
        );
    }

    public static EditorFileContent text(String content) {
        return text(content, EditorCharsets.utf8Bytes(content), EditorCharsets.DEFAULT);
    }

    public static EditorFileContent image(byte[] bytes) {
        return new EditorFileContent(EditorViewMode.IMAGE, null, bytes, null, null);
    }

    public static EditorFileContent hex(String dump) {
        return new EditorFileContent(EditorViewMode.HEX, dump, null, null, null);
    }

    public static EditorFileContent message(String message) {
        return new EditorFileContent(EditorViewMode.MESSAGE, message, null, null, null);
    }

    public EditorViewMode mode() {
        return mode;
    }

    public String text() {
        return text;
    }

    public byte[] imageBytes() {
        return imageBytes == null ? null : Arrays.copyOf(imageBytes, imageBytes.length);
    }

    public byte[] rawBytes() {
        return rawBytes == null ? null : Arrays.copyOf(rawBytes, rawBytes.length);
    }

    public String charsetName() {
        return charsetName;
    }

    public boolean editable() {
        return mode == EditorViewMode.TEXT;
    }

    public boolean supportsEncoding() {
        return mode == EditorViewMode.TEXT && rawBytes != null;
    }
}

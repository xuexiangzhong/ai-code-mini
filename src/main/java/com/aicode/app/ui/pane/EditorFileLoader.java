package com.aicode.app.ui.pane;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Loads workspace files into editor-ready content. */
public final class EditorFileLoader {
    public static final long MAX_EXTENSIONLESS_BYTES = 1024 * 1024;

    private EditorFileLoader() {}

    public static EditorFileContent load(Path path) throws IOException {
        return load(path, EditorCharsets.DEFAULT);
    }

    public static EditorFileContent load(Path path, String charsetName) throws IOException {
        String fileName = path.getFileName().toString();
        String extension = extension(fileName);
        long size = Files.size(path);

        if (extension != null) {
            if ("class".equals(extension)) {
                return EditorFileContent.hex(formatHexDump(Files.readAllBytes(path)));
            }
            if ("png".equals(extension) || "jpg".equals(extension) || "jpeg".equals(extension)) {
                return EditorFileContent.image(Files.readAllBytes(path));
            }
            if (EditorLanguage.isKnownTextExtension(extension)) {
                return loadText(Files.readAllBytes(path), charsetName);
            }
        }

        return loadAsPreviewText(path, size, charsetName);
    }

    public static EditorFileContent redecode(byte[] raw, String charsetName) {
        return EditorFileContent.text(EditorCharsets.decode(raw, charsetName), raw, charsetName);
    }

    public static EditorFileContent redecode(EditorFileContent content, String charsetName) {
        if (!content.supportsEncoding()) {
            return content;
        }
        return redecode(content.rawBytes(), charsetName);
    }

    private static EditorFileContent loadAsPreviewText(Path path, long size, String charsetName) throws IOException {
        if (size > MAX_EXTENSIONLESS_BYTES) {
            return EditorFileContent.message(
                    "文件过大，不支持预览（" + size + " 字节，上限 "
                            + MAX_EXTENSIONLESS_BYTES + " 字节）"
            );
        }
        return loadText(Files.readAllBytes(path), charsetName);
    }

    private static EditorFileContent loadText(byte[] raw, String charsetName) {
        return EditorFileContent.text(EditorCharsets.decode(raw, charsetName), raw, charsetName);
    }

    static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    static String formatHexDump(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 4);
        for (int offset = 0; offset < data.length; offset += 16) {
            sb.append(String.format("%08X  ", offset));
            for (int i = 0; i < 16; i++) {
                if (offset + i < data.length) {
                    sb.append(String.format("%02X ", data[offset + i]));
                } else {
                    sb.append("   ");
                }
            }
            sb.append(' ');
            for (int i = 0; i < 16 && offset + i < data.length; i++) {
                byte value = data[offset + i];
                sb.append(value >= 32 && value < 127 ? (char) value : '.');
            }
            if (offset + 16 < data.length) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}

package com.aicode.app.ui.pane;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Common text encodings for editor open/save and tab switching. */
public final class EditorCharsets {
    public static final String DEFAULT = "UTF-8";

    private static final List<String> CHOICES = List.of(
            "UTF-8",
            "GBK",
            "GB2312",
            "GB18030",
            "ISO-8859-1",
            "Windows-1252",
            "Big5",
            "Shift_JIS",
            "UTF-16",
            "UTF-16LE",
            "UTF-16BE"
    );

    private EditorCharsets() {}

    public static List<String> choices() {
        return CHOICES;
    }

    public static Charset charset(String name) {
        return Charset.forName(name);
    }

    public static String decode(byte[] raw, String charsetName) {
        return new String(raw, charset(charsetName));
    }

    public static byte[] encode(String text, String charsetName) {
        return text.getBytes(charset(charsetName));
    }

    public static byte[] copyOf(byte[] bytes) {
        return bytes == null ? null : bytes.clone();
    }

    public static String normalize(String charsetName) {
        if (charsetName == null || charsetName.isBlank()) {
            return DEFAULT;
        }
        String normalized = charsetName.trim();
        for (String choice : CHOICES) {
            if (choice.equalsIgnoreCase(normalized)) {
                return choice;
            }
        }
        return Charset.forName(normalized).name();
    }

    public static byte[] utf8Bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}

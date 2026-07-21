package com.aicode.app.ui.pane;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.*;

class EditorCharsetsTest {
    @Test
    void roundTripGbk() {
        String original = "中文测试";
        byte[] bytes = EditorCharsets.encode(original, "GBK");
        assertEquals(original, EditorCharsets.decode(bytes, "GBK"));
    }

    @Test
    void normalizeKnownAlias() {
        assertEquals("UTF-8", EditorCharsets.normalize("utf-8"));
        assertEquals("GBK", EditorCharsets.normalize("gbk"));
    }

    @Test
    void decodeUtf8ByDefault() {
        byte[] raw = "hello".getBytes(Charset.forName("UTF-8"));
        assertEquals("hello", EditorCharsets.decode(raw, EditorCharsets.DEFAULT));
    }
}

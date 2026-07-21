package com.aicode.app.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ComposerImageStoreTest {

    @Test
    void savesUnderWorkspace(@TempDir Path workspace) throws Exception {
        byte[] png = new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        Path saved = ComposerImageStore.save(workspace, png, "paste");
        assertTrue(Files.isRegularFile(saved));
        assertTrue(saved.toString().contains(".aicode/composer-images"));
        assertArrayEquals(png, Files.readAllBytes(saved));
    }
}

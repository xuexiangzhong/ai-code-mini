package com.aicode.app.ui;

import com.aicode.agent.llm.ImageBlock;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/** Persists composer paste/drop images under {@code .aicode/composer-images/}. */
public final class ComposerImageStore {
    private static final String SUBDIR = ".aicode/composer-images";
    private static final AtomicInteger PASTE_SEQ = new AtomicInteger();

    private ComposerImageStore() {}

    public static Path save(Path workspace, byte[] pngBytes, String prefix) throws IOException {
        Path dir = storageDir(workspace);
        Files.createDirectories(dir);
        String safePrefix = prefix != null && !prefix.isBlank() ? prefix.strip() : "image";
        String name = safePrefix + "-" + System.currentTimeMillis()
                + "-" + PASTE_SEQ.incrementAndGet() + ".png";
        Path file = dir.resolve(name);
        Files.write(file, pngBytes);
        return file.toAbsolutePath().normalize();
    }

    static Path storageDir(Path workspace) {
        if (workspace != null && Files.isDirectory(workspace)) {
            return workspace.resolve(SUBDIR).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("java.io.tmpdir"), "aicode", "composer-images")
                .toAbsolutePath().normalize();
    }

    public static String displayName(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.toLowerCase(Locale.ROOT).startsWith("paste-")) {
            return "粘贴图片";
        }
        return fileName;
    }

    public static ImageView thumbnailView(Path path, double size) {
        ImageBlock block = ImageBlock.fromPersistedPath(
                path.toAbsolutePath().normalize().toString(),
                ImageBlock.mediaTypeFor(path)
        );
        ImageView preview = new ImageView();
        if (block.hasData()) {
            preview.setImage(new Image(new ByteArrayInputStream(block.data())));
        }
        preview.setFitWidth(size);
        preview.setFitHeight(size);
        preview.setPreserveRatio(true);
        return preview;
    }
}

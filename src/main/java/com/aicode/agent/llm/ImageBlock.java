package com.aicode.agent.llm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/** Image content for multimodal LLM messages. */
public record ImageBlock(String sourcePath, byte[] data, String mediaType) implements ContentBlock {

    public ImageBlock {
        data = data == null ? new byte[0] : Arrays.copyOf(data, data.length);
    }

    public static ImageBlock of(Path path, byte[] bytes) {
        return new ImageBlock(
                path.toAbsolutePath().normalize().toString(),
                bytes,
                mediaTypeFor(path)
        );
    }

    public static ImageBlock fromPersistedPath(String path, String mediaType) {
        try {
            Path file = Path.of(path);
            if (Files.isRegularFile(file)) {
                return new ImageBlock(path, Files.readAllBytes(file), mediaType);
            }
        } catch (Exception ignored) {
            // fall through
        }
        return new ImageBlock(path, new byte[0], mediaType != null ? mediaType : "image/png");
    }

    public String base64Data() {
        return Base64.getEncoder().encodeToString(data);
    }

    public boolean hasData() {
        return data.length > 0;
    }

    public static String mediaTypeFor(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    public static boolean isImagePath(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp");
    }
}

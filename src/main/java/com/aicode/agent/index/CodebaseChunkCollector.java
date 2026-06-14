package com.aicode.agent.index;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Walk workspace and split source files into overlapping line chunks. */
public final class CodebaseChunkCollector {
    public record Chunk(String filePath, int startLine, int endLine, String text) {}

    private static final Set<String> SKIP_DIRS = Set.of(
            "node_modules", ".git", "__pycache__", ".venv", "dist", "build",
            ".next", ".cache", "coverage", "target", ".idea", ".vscode"
    );

    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "kt", "ts", "tsx", "js", "jsx", "py", "go", "rs", "c", "cc", "cpp", "h",
            "cs", "rb", "php", "swift", "scala", "md", "yaml", "yml", "json", "xml", "html",
            "css", "scss", "sql", "sh", "gradle", "properties", "toml", "fxml"
    );

    private static final int MAX_FILES = 800;
    private static final int MAX_FILE_BYTES = 120_000;
    private static final int CHUNK_LINES = 45;
    private static final int CHUNK_OVERLAP = 8;
    static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-Z0-9_\\u4e00-\\u9fff]+");

    private CodebaseChunkCollector() {}

    public static List<Chunk> collect(Path root, String pathFilter) {
        Path searchRoot = pathFilter != null && !pathFilter.isBlank()
                ? root.resolve(pathFilter).normalize()
                : root;
        if (!Files.isDirectory(searchRoot)) {
            return List.of();
        }

        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (files.size() >= MAX_FILES) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!dir.equals(searchRoot)) {
                        String name = dir.getFileName().toString();
                        if (SKIP_DIRS.contains(name)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (files.size() >= MAX_FILES || !isSearchable(file) || attrs.size() > MAX_FILE_BYTES) {
                        return FileVisitResult.CONTINUE;
                    }
                    files.add(file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // partial ok
        }

        List<Chunk> chunks = new ArrayList<>();
        for (Path file : files) {
            chunks.addAll(chunkFile(root, file));
        }
        return chunks;
    }

    private static List<Chunk> chunkFile(Path root, Path file) {
        String content;
        try {
            byte[] raw = Files.readAllBytes(file);
            if (isBinary(raw)) {
                return List.of();
            }
            content = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return List.of();
        }

        String rel = root.relativize(file).toString().replace('\\', '/');
        String[] lines = content.split("\n", -1);
        if (lines.length == 0) {
            return List.of();
        }

        List<Chunk> chunks = new ArrayList<>();
        int step = Math.max(1, CHUNK_LINES - CHUNK_OVERLAP);
        for (int start = 0; start < lines.length; start += step) {
            int end = Math.min(lines.length, start + CHUNK_LINES);
            StringBuilder chunkText = new StringBuilder();
            for (int i = start; i < end; i++) {
                if (i > start) {
                    chunkText.append('\n');
                }
                chunkText.append(lines[i]);
            }
            String text = chunkText.toString();
            if (!text.isBlank()) {
                chunks.add(new Chunk(rel, start + 1, end, text));
            }
            if (end >= lines.length) {
                break;
            }
        }
        return chunks;
    }

    public static List<String> tokenize(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String part : TOKEN_SPLIT.split(query.toLowerCase(Locale.ROOT))) {
            if (part.length() >= 2) {
                terms.add(part);
            }
        }
        return terms;
    }

    private static boolean isSearchable(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        return CODE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static boolean isBinary(byte[] data) {
        int check = Math.min(data.length, 8192);
        for (int i = 0; i < check; i++) {
            if (data[i] == 0) {
                return true;
            }
        }
        return false;
    }
}

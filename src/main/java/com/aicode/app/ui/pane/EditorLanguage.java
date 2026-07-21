package com.aicode.app.ui.pane;

import java.nio.file.Path;
import java.util.Locale;

/** Maps file extensions to Monaco language ids. */
public final class EditorLanguage {
    private EditorLanguage() {}

    public static String detect(Path path) {
        if (path == null) {
            return "plaintext";
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return guessByName(name);
        }
        String language = languageForExtension(name.substring(dot + 1));
        return language != null ? language : "plaintext";
    }

    public static boolean isKnownTextExtension(String extension) {
        if (extension == null || extension.isEmpty()) {
            return false;
        }
        return languageForExtension(extension.toLowerCase(Locale.ROOT)) != null;
    }

    private static String languageForExtension(String extension) {
        return switch (extension) {
            case "java" -> "java";
            case "js", "mjs", "cjs" -> "javascript";
            case "ts" -> "typescript";
            case "tsx" -> "typescript";
            case "jsx" -> "javascript";
            case "json" -> "json";
            case "xml" -> "xml";
            case "html", "htm" -> "html";
            case "css" -> "css";
            case "scss" -> "scss";
            case "md", "markdown" -> "markdown";
            case "yaml", "yml" -> "yaml";
            case "py" -> "python";
            case "go" -> "go";
            case "rs" -> "rust";
            case "sql" -> "sql";
            case "sh", "bash", "zsh" -> "shell";
            case "gradle" -> "java";
            case "fxml" -> "xml";
            case "properties" -> "ini";
            case "toml" -> "ini";
            case "kt" -> "kotlin";
            case "cpp", "cc", "cxx", "h", "hpp" -> "cpp";
            case "c" -> "c";
            case "php" -> "php";
            case "rb" -> "ruby";
            case "swift" -> "swift";
            case "vue" -> "html";
            case "txt" -> "plaintext";
            default -> null;
        };
    }

    private static String guessByName(String name) {
        return switch (name) {
            case "dockerfile" -> "dockerfile";
            case "makefile" -> "makefile";
            case "readme" -> "markdown";
            default -> "plaintext";
        };
    }
}

package com.aicode.app.config;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Normalizes {@code user.dir} for packaged desktop launches (macOS Finder often sets it to {@code /}).
 */
public final class WorkingDirectory {
    private WorkingDirectory() {}

    /**
     * @return effective working directory for {@link AppConfigStore} (config file location)
     */
    public static Path effective() {
        ensureSensible();
        return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    /**
     * Default project workspace for first launch. Must not be filesystem root or entire home
     * (scanning those directories freezes the UI file tree).
     */
    public static Path defaultWorkspace() {
        ensureSensible();
        Path docs = Path.of(System.getProperty("user.home"), "Documents").toAbsolutePath().normalize();
        if (Files.isDirectory(docs)) {
            return docs;
        }
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        return Files.isDirectory(home) ? home : effective();
    }

    /** macOS .app / Windows 快捷方式启动时 user.dir 常为 {@code /}，回退到用户主目录。 */
    public static void ensureSensible() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (isUnusableForConfig(dir)) {
            Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
            System.setProperty("user.dir", home.toString());
        }
    }

    /** {@code /} 等路径不能作为配置目录或工作区根。 */
    static boolean isUnusableForConfig(Path dir) {
        if (dir.getFileName() == null) {
            return true;
        }
        String s = dir.toString();
        return "/".equals(s) || "\\".equals(s);
    }

    /** 过滤 {@code /}、整个 {@code ~} 等不宜作为 IDE 工作区的路径。 */
    public static Path normalizeWorkspace(Path workspace) {
        if (workspace == null) {
            return defaultWorkspace();
        }
        Path path = workspace.toAbsolutePath().normalize();
        if (isUnusableForConfig(path) || isEntireHomeDirectory(path)) {
            return defaultWorkspace();
        }
        return path;
    }

    /** 整个用户主目录不宜作为 IDE 默认工作区（文件过多）。 */
    static boolean isEntireHomeDirectory(Path dir) {
        Path home = Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        return dir.toAbsolutePath().normalize().equals(home);
    }

    /** TreeView 节点显示名（根路径无 fileName）。 */
    public static String displayName(Path path) {
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }
}

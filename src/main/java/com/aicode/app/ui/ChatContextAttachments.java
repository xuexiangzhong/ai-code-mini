package com.aicode.app.ui;

import com.aicode.app.config.WorkingDirectory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Chat context attachments (@ file / @ selection). */
public final class ChatContextAttachments {
    public sealed interface Attachment {
        record File(Path path, String content) implements Attachment {}
        record Selection(Path path, String text) implements Attachment {}
    }

    private final List<Attachment> items = new ArrayList<>();

    public List<Attachment> items() {
        return Collections.unmodifiableList(items);
    }

    public void clear() {
        items.clear();
    }

    public void addFile(Path path, String content) {
        items.removeIf(a -> a instanceof Attachment.File f && f.path().equals(path));
        items.add(new Attachment.File(path, content));
    }

    public void addSelection(Path path, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        items.removeIf(a -> a instanceof Attachment.Selection);
        items.add(new Attachment.Selection(path, text.strip()));
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public String buildPromptPrefix() {
        if (items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Attachment item : items) {
            switch (item) {
                case Attachment.File file -> sb.append("[@文件: ")
                        .append(file.path())
                        .append("]\n```\n")
                        .append(truncate(file.content(), 12000))
                        .append("\n```\n\n");
                case Attachment.Selection sel -> sb.append("[@选中代码: ")
                        .append(sel.path() != null ? sel.path() : "editor")
                        .append("]\n```\n")
                        .append(sel.text())
                        .append("\n```\n\n");
            }
        }
        return sb.toString();
    }

    public String describeChips() {
        if (items.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Attachment item : items) {
            switch (item) {
                case Attachment.File file ->
                        parts.add("@" + WorkingDirectory.displayName(file.path()));
                case Attachment.Selection sel ->
                        parts.add("@选中(" + (sel.path() != null
                                ? WorkingDirectory.displayName(sel.path()) : "editor") + ")");
            }
        }
        return String.join("  ", parts);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n…(已截断)";
    }
}

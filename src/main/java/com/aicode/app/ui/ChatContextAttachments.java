package com.aicode.app.ui;

import com.aicode.agent.ContextSanitizer;
import com.aicode.agent.llm.ContentBlock;
import com.aicode.agent.llm.ImageBlock;
import com.aicode.agent.llm.TextBlock;
import com.aicode.agent.tools.CodebaseSearch;
import com.aicode.agent.tools.ListDirTool;
import com.aicode.app.config.WorkingDirectory;
import com.aicode.app.session.UserMessagePayload;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Chat context attachments (@ file / @ folder / @ selection / @ codebase / @ image). */
public final class ChatContextAttachments {
    public sealed interface Attachment {
        record File(Path path, String content) implements Attachment {}
        record Image(Path path, byte[] data, String mediaType) implements Attachment {}
        record Folder(Path path, String summary) implements Attachment {}
        record Selection(Path path, String text) implements Attachment {}
        record Codebase() implements Attachment {}
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

    public void addImage(Path path, byte[] data, String mediaType) {
        items.removeIf(a -> a instanceof Attachment.Image img && img.path().equals(path));
        items.add(new Attachment.Image(path, data, mediaType));
    }

    public boolean removeImage(Path path) {
        return items.removeIf(a -> a instanceof Attachment.Image img && img.path().equals(path));
    }

    public List<Attachment.Image> images() {
        return items.stream()
                .filter(Attachment.Image.class::isInstance)
                .map(Attachment.Image.class::cast)
                .toList();
    }

    public void addFolder(Path path, String summary) {
        items.removeIf(a -> a instanceof Attachment.Folder f && f.path().equals(path));
        items.add(new Attachment.Folder(path, summary));
    }

    public void addCodebase() {
        items.removeIf(a -> a instanceof Attachment.Codebase);
        items.add(new Attachment.Codebase());
    }

    public void addSelection(Path path, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        items.removeIf(a -> a instanceof Attachment.Selection);
        items.add(new Attachment.Selection(path, text.strip()));
    }

    public boolean hasCodebase() {
        return items.stream().anyMatch(a -> a instanceof Attachment.Codebase);
    }

    public boolean hasImages() {
        return items.stream().anyMatch(a -> a instanceof Attachment.Image);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** Build user message including @ context (text and optional images for vision models). */
    public UserMessagePayload buildUserMessage(String userText, Path workspace) {
        String textBody = buildTextBody(userText, workspace);
        if (!hasImages()) {
            return UserMessagePayload.text(textBody, userText != null ? userText : "");
        }
        List<ContentBlock> blocks = new ArrayList<>();
        if (textBody != null && !textBody.isBlank()) {
            blocks.add(new TextBlock(textBody));
        }
        for (Attachment item : items) {
            if (item instanceof Attachment.Image img) {
                blocks.add(ImageBlock.of(img.path(), img.data()));
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(new TextBlock(""));
        }
        return UserMessagePayload.blocks(blocks, userText != null ? userText : "");
    }

    /** @deprecated use {@link #buildUserMessage(String, Path)} */
    @Deprecated
    public String buildFullPrompt(String userText, Path workspace) {
        return buildUserMessage(userText, workspace).message().isStringContent()
                ? buildUserMessage(userText, workspace).message().contentText()
                : textBodyOnly(userText, workspace);
    }

    private String textBodyOnly(String userText, Path workspace) {
        return buildTextBody(userText, workspace);
    }

    private String buildTextBody(String userText, Path workspace) {
        StringBuilder sb = new StringBuilder();
        if (hasCodebase() && userText != null && !userText.isBlank()) {
            CodebaseSearch.Result result = CodebaseSearch.search(workspace, userText, 8, null, null);
            String search = CodebaseSearch.formatResults(result);
            sb.append("[@Codebase 检索]\n")
                    .append(ContextSanitizer.wrapUntrusted(
                            "codebase_search",
                            truncate(search, 16_000)
                    ))
                    .append("\n\n");
        }
        sb.append(buildPromptPrefix());
        if (userText != null && !userText.isBlank()) {
            sb.append(userText);
        }
        return sb.toString();
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
                        .append("]\n")
                        .append(ContextSanitizer.wrapUntrusted(
                                file.path().toString(),
                                truncate(file.content(), 12_000)
                        ))
                        .append("\n\n");
                case Attachment.Image img -> sb.append("[@图片: ")
                        .append(img.path())
                        .append("]\n")
                        .append("(已作为图像附件发送给模型)\n\n");
                case Attachment.Folder folder -> sb.append("[@文件夹: ")
                        .append(folder.path())
                        .append("]\n")
                        .append(ContextSanitizer.wrapUntrusted(
                                folder.path().toString(),
                                truncate(folder.summary(), 10_000)
                        ))
                        .append("\n\n");
                case Attachment.Selection sel -> sb.append("[@选中代码: ")
                        .append(sel.path() != null ? sel.path() : "editor")
                        .append("]\n")
                        .append(ContextSanitizer.wrapUntrusted(
                                sel.path() != null ? sel.path().toString() : "selection",
                                sel.text()
                        ))
                        .append("\n\n");
                case Attachment.Codebase ignored -> { /* resolved in buildTextBody */ }
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
                case Attachment.Image img ->
                        parts.add("@图片(" + WorkingDirectory.displayName(img.path()) + ")");
                case Attachment.Folder folder ->
                        parts.add("@文件夹(" + WorkingDirectory.displayName(folder.path()) + ")");
                case Attachment.Selection sel ->
                        parts.add("@选中(" + (sel.path() != null
                                ? WorkingDirectory.displayName(sel.path()) : "editor") + ")");
                case Attachment.Codebase ignored -> parts.add("@Codebase");
            }
        }
        return String.join("  ", parts);
    }

    public static String summarizeFolder(Path workspace, Path folder) {
        Path rel = workspace.toAbsolutePath().normalize().relativize(folder.toAbsolutePath().normalize());
        String pathArg = rel.toString().isEmpty() ? "." : rel.toString().replace('\\', '/');
        return ListDirTool.execute(new ListDirTool.Input(pathArg, 2));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "\n…(已截断)";
    }
}

package com.aicode.app.ui;

import com.aicode.agent.llm.ImageBlock;
import com.aicode.app.config.WorkingDirectory;
import com.aicode.app.ui.pane.EditorPane;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Cursor-style composer: @ mentions, paste/drag images, thumbnail chips above the text area.
 */
public final class AtMentionInput extends VBox implements ChatComposerInput {
    private static final int POPUP_LIMIT = 10;
    private static final double ROW_HEIGHT = 34;
    private static final double THUMB_SIZE = 56;

    public sealed interface MentionOption {
        String label();
        String insertToken();

        record CurrentFile(Path path) implements MentionOption {
            @Override
            public String label() {
                return "当前文件 · " + WorkingDirectory.displayName(path);
            }

            @Override
            public String insertToken() {
                return "@" + WorkingDirectory.displayName(path);
            }
        }

        record Selection(Path path) implements MentionOption {
            @Override
            public String label() {
                return "选中代码" + (path != null ? " · " + WorkingDirectory.displayName(path) : "");
            }

            @Override
            public String insertToken() {
                return "@selection";
            }
        }

        record File(WorkspaceFileIndex.Entry entry) implements MentionOption {
            @Override
            public String label() {
                return entry.relative();
            }

            @Override
            public String insertToken() {
                return "@" + entry.relative();
            }
        }

        record Folder(WorkspaceFileIndex.DirEntry entry) implements MentionOption {
            @Override
            public String label() {
                return "📁 " + entry.relative();
            }

            @Override
            public String insertToken() {
                return "@folder:" + entry.relative();
            }
        }

        record Codebase() implements MentionOption {
            @Override
            public String label() {
                return "Codebase · 语义检索代码库";
            }

            @Override
            public String insertToken() {
                return "@Codebase";
            }
        }
    }

    private final FlowPane attachmentBar = new FlowPane(6, 6);
    private final TextArea textArea = new TextArea();
    private final HBox composerToolbar = new HBox(6);
    private final Button attachImageButton = new Button("🖼");
    private final ChatContextAttachments attachments = new ChatContextAttachments();
    private final Popup popup = new Popup();
    private final ListView<MentionOption> optionList = new ListView<>();
    private final List<MentionOption> filtered = new ArrayList<>();

    private Path workspaceRoot;
    private WorkspaceFileIndex fileIndex = new WorkspaceFileIndex();
    private Supplier<Path> activeFileSupplier = () -> null;
    private Supplier<EditorPane> editorPaneSupplier = () -> null;
    private BiConsumer<Path, java.util.function.Consumer<String>> fileLoader;
    private Runnable onSubmit;
    private int mentionStart = -1;
    private boolean popupOpen;
    private boolean mentionSyncGuard;
    private int pendingMentionStart = -1;

    public AtMentionInput() {
        getStyleClass().add("at-mention-input");
        attachmentBar.getStyleClass().add("composer-attachment-bar");
        attachmentBar.setVisible(false);
        attachmentBar.setManaged(false);

        textArea.getStyleClass().add("at-mention-textarea");
        textArea.setPrefRowCount(3);
        textArea.setWrapText(true);
        VBox.setVgrow(textArea, Priority.ALWAYS);

        attachImageButton.getStyleClass().add("composer-attach-image-btn");
        attachImageButton.setFocusTraversable(false);
        attachImageButton.setTooltip(new Tooltip("添加图片"));
        attachImageButton.setOnAction(e -> pickImages());
        composerToolbar.getStyleClass().add("composer-toolbar");
        composerToolbar.getChildren().add(attachImageButton);

        getChildren().addAll(attachmentBar, textArea, composerToolbar);

        installImagePasteAndDrop();

        optionList.getStyleClass().add("at-mention-popup-list");
        optionList.setPrefWidth(360);
        optionList.setFixedCellSize(ROW_HEIGHT);
        optionList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(MentionOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setGraphic(null);
                setText(item.label());
            }
        });
        optionList.setOnMouseClicked(e -> {
            if (optionList.getSelectionModel().getSelectedItem() != null) {
                applySelection(optionList.getSelectionModel().getSelectedItem());
            }
        });
        VBox popupRoot = new VBox(optionList);
        popupRoot.getStyleClass().add("at-mention-popup");
        popup.getContent().add(popupRoot);
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        popup.setOnHidden(e -> popupOpen = false);

        textArea.textProperty().addListener((obs, old, text) -> onTextChanged());
        textArea.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
    }

    public void bindWorkspace(Path root) {
        this.workspaceRoot = root;
        fileIndex = new WorkspaceFileIndex();
        fileIndex.rebuildAsync(root, null);
    }

    public void setActiveFileSupplier(Supplier<Path> activeFileSupplier) {
        this.activeFileSupplier = activeFileSupplier;
    }

    public void setEditorPaneSupplier(Supplier<EditorPane> editorPaneSupplier) {
        this.editorPaneSupplier = editorPaneSupplier;
    }

    public void setFileLoader(BiConsumer<Path, java.util.function.Consumer<String>> fileLoader) {
        this.fileLoader = fileLoader;
    }

    @Override
    public Node node() {
        return this;
    }

    @Override
    public String getText() {
        return textArea.getText();
    }

    @Override
    public void clear() {
        textArea.clear();
        hidePopup();
    }

    @Override
    public void disableInput(boolean disabled) {
        textArea.setDisable(disabled);
        attachImageButton.setDisable(disabled);
    }

    @Override
    public void setPromptText(String text) {
        textArea.setPromptText(text);
    }

    @Override
    public void requestFocus() {
        textArea.requestFocus();
    }

    @Override
    public ChatContextAttachments attachments() {
        return attachments;
    }

    @Override
    public void clearAfterSend() {
        attachments.clear();
        refreshAttachmentBar();
        clear();
    }

    @Override
    public Path activeFile() {
        return activeFileSupplier.get();
    }

    @Override
    public void setOnSubmit(Runnable onSubmit) {
        this.onSubmit = onSubmit;
    }

    private void onTextChanged() {
        if (mentionSyncGuard) {
            return;
        }
        String text = textArea.getText();
        if (text == null || text.isEmpty()) {
            hidePopup();
            return;
        }
        int caret = clampCaret(textArea.getCaretPosition(), text.length());
        mentionStart = findMentionStart(text, caret);
        if (mentionStart < 0) {
            hidePopup();
            return;
        }
        int queryEnd = Math.max(mentionStart + 1, caret);
        if (queryEnd > text.length()) {
            hidePopup();
            return;
        }
        String query = text.substring(mentionStart + 1, queryEnd);
        if (query.contains(" ") || query.contains("\n")) {
            hidePopup();
            return;
        }
        showPopup(query);
    }

    private void onKeyPressed(KeyEvent event) {
        if (isPasteShortcut(event) && tryPasteImageFromClipboard()) {
            event.consume();
            return;
        }
        if (popupOpen) {
            if (event.getCode() == KeyCode.ESCAPE) {
                hidePopup();
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.UP) {
                moveSelection(-1);
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.DOWN) {
                moveSelection(1);
                event.consume();
                return;
            }
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.TAB) {
                MentionOption selected = optionList.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    applySelection(selected);
                }
                event.consume();
                return;
            }
        }
        if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
            if (onSubmit != null) {
                onSubmit.run();
            }
            event.consume();
        }
    }

    private void moveSelection(int delta) {
        int size = optionList.getItems().size();
        if (size == 0) {
            return;
        }
        int idx = optionList.getSelectionModel().getSelectedIndex();
        if (idx < 0) {
            idx = 0;
        } else {
            idx = Math.floorMod(idx + delta, size);
        }
        optionList.getSelectionModel().select(idx);
        optionList.scrollTo(idx);
    }

    private void showPopup(String query) {
        rebuildOptions(query);
        if (filtered.isEmpty()) {
            hidePopup();
            return;
        }
        optionList.getItems().setAll(filtered);
        optionList.getSelectionModel().select(0);
        int rows = Math.min(filtered.size(), POPUP_LIMIT);
        double listHeight = rows * ROW_HEIGHT;
        optionList.setMinHeight(listHeight);
        optionList.setPrefHeight(listHeight);
        optionList.setMaxHeight(listHeight);
        if (!popupOpen) {
            Bounds bounds = textArea.localToScreen(textArea.getLayoutBounds());
            if (bounds != null) {
                popup.show(textArea, bounds.getMinX(), bounds.getMaxY() + 4);
            } else {
                popup.show(textArea, 0, 0);
            }
            popupOpen = true;
        }
        Bounds caretBounds = textArea.localToScreen(textArea.getLayoutBounds());
        if (caretBounds != null) {
            popup.setAnchorX(caretBounds.getMinX());
            popup.setAnchorY(caretBounds.getMaxY() + 4);
        }
    }

    private void hidePopup() {
        if (popupOpen) {
            popup.hide();
            popupOpen = false;
        }
        mentionStart = -1;
    }

    private void rebuildOptions(String query) {
        filtered.clear();
        Path active = activeFileSupplier.get();
        if (query.isEmpty() || "selection".startsWith(query.toLowerCase())) {
            filtered.add(new MentionOption.Selection(active));
        }
        if (query.isEmpty() || "codebase".startsWith(query.toLowerCase())) {
            filtered.add(new MentionOption.Codebase());
        }
        if (active != null && (query.isEmpty()
                || WorkingDirectory.displayName(active).toLowerCase().contains(query.toLowerCase()))) {
            filtered.add(new MentionOption.CurrentFile(active));
        }
        int remaining = POPUP_LIMIT - filtered.size();
        if (remaining > 0) {
            for (WorkspaceFileIndex.DirEntry entry : fileIndex.searchDirectories(query, Math.min(4, remaining))) {
                filtered.add(new MentionOption.Folder(entry));
                remaining--;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        if (remaining > 0) {
            for (WorkspaceFileIndex.Entry entry : fileIndex.search(query, remaining)) {
                filtered.add(new MentionOption.File(entry));
            }
        }
    }

    private void applySelection(MentionOption option) {
        String text = textArea.getText();
        int caret = clampCaret(textArea.getCaretPosition(), text != null ? text.length() : 0);
        pendingMentionStart = mentionStart >= 0 ? mentionStart : findMentionStart(text, caret);
        hidePopup();
        switch (option) {
            case MentionOption.CurrentFile cf -> attachFile(cf.path(), cf.insertToken());
            case MentionOption.File f -> attachFile(f.entry().absolute(), f.insertToken());
            case MentionOption.Folder folder -> attachFolder(folder.entry().absolute(), folder.insertToken());
            case MentionOption.Codebase codebase -> attachCodebase(codebase.insertToken());
            case MentionOption.Selection sel -> attachSelection(sel);
        }
    }

    private void attachCodebase(String token) {
        attachments.addCodebase();
        insertToken(token + " ");
    }

    private void attachFolder(Path path, String token) {
        Runnable insert = () -> insertToken(token + " ");
        if (workspaceRoot == null) {
            insert.run();
            return;
        }
        Thread.ofVirtual().name("mention-folder-load").start(() -> {
            String summary = ChatContextAttachments.summarizeFolder(workspaceRoot, path);
            Platform.runLater(() -> {
                attachments.addFolder(path, summary);
                insert.run();
            });
        });
    }

    private void attachFile(Path path, String token) {
        Runnable insert = () -> {
            if (token != null && !token.isBlank()) {
                insertToken(token + " ");
            }
        };
        if (ImageBlock.isImagePath(path)) {
            String mentionToken = token != null && !token.isBlank() ? token : null;
            Thread.ofVirtual().name("mention-image-load").start(() -> {
                try {
                    byte[] bytes = Files.readAllBytes(path);
                    String mediaType = ImageBlock.mediaTypeFor(path);
                    Platform.runLater(() -> attachImagePath(path, bytes, mediaType, mentionToken));
                } catch (IOException e) {
                    if (mentionToken != null) {
                        Platform.runLater(() -> insertToken(mentionToken + " "));
                    }
                }
            });
            return;
        }
        if (fileLoader != null) {
            fileLoader.accept(path, content -> Platform.runLater(() -> {
                attachments.addFile(path, content);
                insert.run();
            }));
            return;
        }
        Thread.ofVirtual().name("mention-file-load").start(() -> {
            try {
                String content = Files.readString(path);
                Platform.runLater(() -> {
                    attachments.addFile(path, content);
                    insert.run();
                });
            } catch (IOException e) {
                Platform.runLater(insert);
            }
        });
    }

    private void attachSelection(MentionOption.Selection sel) {
        EditorPane pane = editorPaneSupplier.get();
        if (pane == null) {
            insertToken(sel.insertToken() + " ");
            return;
        }
        pane.getSelectionAsync().thenAccept(text -> Platform.runLater(() -> {
            if (text != null && !text.isBlank()) {
                attachments.addSelection(sel.path(), text);
            }
            insertToken(sel.insertToken() + " ");
        }));
    }

    private void insertToken(String token) {
        mentionSyncGuard = true;
        try {
            String text = textArea.getText();
            int caret = clampCaret(textArea.getCaretPosition(), text.length());
            int start = pendingMentionStart >= 0 ? pendingMentionStart : mentionStart;
            pendingMentionStart = -1;
            if (start < 0) {
                start = findMentionStart(text, caret);
            }
            if (start < 0) {
                textArea.appendText(token);
                textArea.positionCaret(textArea.getText().length());
                return;
            }
            String before = text.substring(0, start);
            String after = caret <= text.length() ? text.substring(caret) : "";
            String updated = before + token + after;
            textArea.setText(updated);
            int newCaret = before.length() + token.length();
            textArea.positionCaret(newCaret);
        } finally {
            mentionSyncGuard = false;
            mentionStart = -1;
        }
    }

    private static int clampCaret(int caret, int textLength) {
        if (caret < 0) {
            return 0;
        }
        return Math.min(caret, textLength);
    }

    private static int findMentionStart(String text, int caret) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        int safeCaret = clampCaret(caret, text.length());
        if (safeCaret <= 0) {
            return -1;
        }
        int i = safeCaret - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (c == '@') {
                if (i == 0 || Character.isWhitespace(text.charAt(i - 1))) {
                    return i;
                }
                return -1;
            }
            if (Character.isWhitespace(c)) {
                return -1;
            }
            i--;
        }
        return -1;
    }

    private void installImagePasteAndDrop() {
        setOnDragOver(this::onDragOver);
        setOnDragDropped(this::onDragDropped);
        textArea.setOnDragOver(this::onDragOver);
        textArea.setOnDragDropped(this::onDragDropped);
    }

    private void onDragOver(DragEvent event) {
        if (event.getDragboard().hasImage() || hasImageFiles(event.getDragboard())) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void onDragDropped(DragEvent event) {
        Dragboard board = event.getDragboard();
        boolean handled = false;
        if (board.hasImage()) {
            Image image = board.getImage();
            if (image != null && image.getWidth() > 0) {
                attachClipboardImage(image);
                handled = true;
            }
        }
        if (!handled && board.hasFiles()) {
            for (java.io.File file : board.getFiles()) {
                Path path = file.toPath();
                if (ImageBlock.isImagePath(path)) {
                    attachFile(path, "");
                    handled = true;
                }
            }
        }
        event.setDropCompleted(handled);
        event.consume();
    }

    private static boolean hasImageFiles(Dragboard board) {
        if (!board.hasFiles()) {
            return false;
        }
        for (java.io.File file : board.getFiles()) {
            if (ImageBlock.isImagePath(file.toPath())) {
                return true;
            }
        }
        return false;
    }

    private boolean isPasteShortcut(KeyEvent event) {
        return event.getCode() == KeyCode.V && event.isShortcutDown() && !event.isShiftDown();
    }

    private boolean tryPasteImageFromClipboard() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasImage()) {
            Image image = clipboard.getImage();
            if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
                attachClipboardImage(image);
                return true;
            }
        }
        if (clipboard.hasFiles()) {
            for (java.io.File file : clipboard.getFiles()) {
                Path path = file.toPath();
                if (ImageBlock.isImagePath(path)) {
                    attachFile(path, "");
                    return true;
                }
            }
        }
        return false;
    }

    private void attachClipboardImage(Image fxImage) {
        Thread.ofVirtual().name("composer-paste-image").start(() -> {
            try {
                byte[] png = ImageBytes.toPng(fxImage);
                Path saved = ComposerImageStore.save(workspaceRoot, png, "paste");
                Platform.runLater(() -> attachImagePath(saved, png, "image/png", null));
            } catch (IOException ignored) {
                // fall through — let default text paste happen on next attempt
            }
        });
    }

    private void pickImages() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择图片");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("图片", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.webp")
        );
        Stage stage = getScene() != null && getScene().getWindow() instanceof Stage s ? s : null;
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files == null) {
            return;
        }
        for (File file : files) {
            Path path = file.toPath();
            if (ImageBlock.isImagePath(path)) {
                attachFile(path, "");
            }
        }
    }

    private void attachImagePath(Path path, byte[] bytes, String mediaType, String mentionToken) {
        attachments.addImage(path, bytes, mediaType);
        refreshAttachmentBar();
        if (mentionToken != null && !mentionToken.isBlank()) {
            insertToken(mentionToken.endsWith(" ") ? mentionToken : mentionToken + " ");
        }
    }

    private void refreshAttachmentBar() {
        attachmentBar.getChildren().clear();
        List<ChatContextAttachments.Attachment.Image> images = attachments.images();
        if (images.isEmpty()) {
            attachmentBar.setVisible(false);
            attachmentBar.setManaged(false);
            return;
        }
        for (ChatContextAttachments.Attachment.Image img : images) {
            attachmentBar.getChildren().add(buildImageChip(img));
        }
        attachmentBar.setVisible(true);
        attachmentBar.setManaged(true);
    }

    private StackPane buildImageChip(ChatContextAttachments.Attachment.Image img) {
        ImageView preview = new ImageView(new Image(new java.io.ByteArrayInputStream(img.data())));
        preview.setFitWidth(THUMB_SIZE);
        preview.setFitHeight(THUMB_SIZE);
        preview.setPreserveRatio(true);
        javafx.scene.control.Label remove = new javafx.scene.control.Label("×");
        remove.getStyleClass().add("composer-attachment-remove");
        remove.setOnMouseClicked(e -> {
            attachments.removeImage(img.path());
            refreshAttachmentBar();
        });

        StackPane chip = new StackPane(preview, remove);
        chip.getStyleClass().add("composer-attachment-chip");
        StackPane.setAlignment(remove, javafx.geometry.Pos.TOP_RIGHT);
        Tooltip.install(chip, new Tooltip(ComposerImageStore.displayName(img.path())));
        return chip;
    }
}

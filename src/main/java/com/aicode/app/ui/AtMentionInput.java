package com.aicode.app.ui;

import com.aicode.app.config.WorkingDirectory;
import com.aicode.app.ui.pane.EditorPane;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Cursor-style inline @ mentions inside the composer text area.
 * Type {@code @} to open file / selection picker; chosen items appear as {@code @token} in text.
 */
public final class AtMentionInput extends VBox implements ChatComposerInput {
    private static final int POPUP_LIMIT = 10;
    private static final double ROW_HEIGHT = 34;

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
    }

    private final TextArea textArea = new TextArea();
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
        textArea.getStyleClass().add("at-mention-textarea");
        textArea.setPrefRowCount(3);
        textArea.setWrapText(true);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        getChildren().add(textArea);

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
        clear();
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
        if (active != null && (query.isEmpty()
                || WorkingDirectory.displayName(active).toLowerCase().contains(query.toLowerCase()))) {
            filtered.add(new MentionOption.CurrentFile(active));
        }
        int remaining = POPUP_LIMIT - filtered.size();
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
            case MentionOption.Selection sel -> attachSelection(sel);
        }
    }

    private void attachFile(Path path, String token) {
        Runnable insert = () -> insertToken(token + " ");
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
}

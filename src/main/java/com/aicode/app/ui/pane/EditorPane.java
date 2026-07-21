package com.aicode.app.ui.pane;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import netscape.javascript.JSObject;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

/** WebView wrapper around Monaco Editor (monaco.html). */
public final class EditorPane extends StackPane {
    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    private volatile boolean ready;
    private Runnable onDirty;
    private Timeline dirtyPoller;
    private volatile boolean jsDirtyReported;

    public EditorPane() {
        getChildren().add(webView);
        webView.setContextMenuEnabled(false);
        installClipboardShortcuts();
        installScrollNormalization();
        installSceneAccelerators();
        installContextMenu();
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                ready = true;
                installBridge();
            }
        });
        URL page = getClass().getResource("/editor/monaco.html");
        if (page != null) {
            engine.load(page.toExternalForm());
        }
    }

    private void installClipboardShortcuts() {
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleClipboardKey);
        webView.addEventFilter(KeyEvent.KEY_PRESSED, this::handleClipboardKey);
    }

    private void installScrollNormalization() {
        addEventFilter(ScrollEvent.SCROLL, this::handleScrollNormalize);
        webView.addEventFilter(ScrollEvent.SCROLL, this::handleScrollNormalize);
    }

    /**
     * JavaFX WebView on Windows often delivers oversized wheel deltas to the embedded page,
     * which makes Monaco jump dozens of lines per notch. Normalize here and scroll via JS.
     */
    private void handleScrollNormalize(ScrollEvent event) {
        event.consume();
        // Match WebView.processScrollEvent: JavaFX deltaY sign is inverted vs browser/Monaco.
        int lines = scrollLinesFromDelta(-event.getDeltaY());
        if (lines == 0) {
            return;
        }
        whenReady(() -> engine.executeScript("scrollByLines(" + lines + ");"));
    }

    private static int scrollLinesFromDelta(double deltaY) {
        if (deltaY == 0) {
            return 0;
        }
        int magnitude = (int) Math.round(Math.abs(deltaY) / 40.0);
        magnitude = Math.max(1, Math.min(5, magnitude));
        return deltaY > 0 ? magnitude : -magnitude;
    }

    private void installSceneAccelerators() {
        sceneProperty().addListener((obs, oldScene, scene) -> {
            if (oldScene != null) {
                unbindAccelerators(oldScene);
            }
            if (scene != null) {
                bindAccelerators(scene);
            }
        });
    }

    private void bindAccelerators(Scene scene) {
        scene.getAccelerators().put(shortcut(KeyCode.C), () -> runIfEditorFocused(this::handleCopy));
        scene.getAccelerators().put(shortcut(KeyCode.X), () -> runIfEditorFocused(this::handleCut));
        scene.getAccelerators().put(shortcut(KeyCode.V), () -> runIfEditorFocused(this::handlePaste));
        scene.getAccelerators().put(shortcut(KeyCode.A), () -> runIfEditorFocused(this::handleSelectAll));
    }

    private void unbindAccelerators(Scene scene) {
        scene.getAccelerators().remove(shortcut(KeyCode.C));
        scene.getAccelerators().remove(shortcut(KeyCode.X));
        scene.getAccelerators().remove(shortcut(KeyCode.V));
        scene.getAccelerators().remove(shortcut(KeyCode.A));
    }

    private static KeyCombination shortcut(KeyCode code) {
        return new KeyCodeCombination(code, KeyCombination.SHORTCUT_DOWN);
    }

    private void runIfEditorFocused(Runnable action) {
        if (webView.isFocused()) {
            action.run();
        }
    }

    private void handleClipboardKey(KeyEvent event) {
        if (!webView.isFocused() || !event.isShortcutDown() || event.isAltDown() || event.isShiftDown()) {
            return;
        }
        switch (event.getCode()) {
            case C -> {
                handleCopy();
                event.consume();
            }
            case X -> {
                handleCut();
                event.consume();
            }
            case V -> {
                handlePaste();
                event.consume();
            }
            case A -> {
                handleSelectAll();
                event.consume();
            }
            default -> {}
        }
    }

    private void installContextMenu() {
        MenuItem copy = menuItem("复制", this::handleCopy);
        MenuItem cut = menuItem("剪切", this::handleCut);
        MenuItem paste = menuItem("粘贴", this::handlePaste);
        MenuItem selectAll = menuItem("全选", this::handleSelectAll);
        ContextMenu menu = new ContextMenu(copy, cut, paste, new SeparatorMenuItem(), selectAll);
        webView.setOnContextMenuRequested(event -> {
            requestEditorFocus();
            menu.show(webView, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private static MenuItem menuItem(String label, Runnable action) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> action.run());
        return item;
    }

    private void handleCopy() {
        if (!shouldHandleEditorClipboard()) {
            copyOverlayInputSelection();
            return;
        }
        getSelectionAsync().thenAccept(text -> Platform.runLater(() -> {
            if (text == null || text.isEmpty()) {
                return;
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
        }));
    }

    private void handleCut() {
        if (!shouldHandleEditorClipboard()) {
            cutOverlayInputSelection();
            return;
        }
        getSelectionAsync().thenAccept(text -> Platform.runLater(() -> {
            if (text == null || text.isEmpty()) {
                return;
            }
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
            whenReady(() -> engine.executeScript("deleteSelection();"));
        }));
    }

    private void handlePaste() {
        Object clip = Clipboard.getSystemClipboard().getContent(DataFormat.PLAIN_TEXT);
        if (!(clip instanceof String text) || text.isEmpty()) {
            return;
        }
        boolean editorClipboard = shouldHandleEditorClipboard();
        whenReady(() -> {
            if (editorClipboard) {
                engine.executeScript("insertText(" + jsString(text) + ");");
            } else {
                engine.executeScript("insertIntoFocusedInput(" + jsString(text) + ");");
            }
        });
    }

    private void handleSelectAll() {
        boolean editorClipboard = shouldHandleEditorClipboard();
        whenReady(() -> {
            if (editorClipboard) {
                engine.executeScript("selectAllText();");
            } else {
                engine.executeScript("selectAllFocusedInput();");
            }
        });
    }

    private boolean shouldHandleEditorClipboard() {
        if (!ready) {
            return true;
        }
        try {
            Object result = engine.executeScript(
                    "typeof shouldHandleEditorClipboard === 'function' && shouldHandleEditorClipboard()");
            return !Boolean.FALSE.equals(result);
        } catch (Exception ignored) {
            return true;
        }
    }

    private void copyOverlayInputSelection() {
        whenReady(() -> {
            try {
                Object result = engine.executeScript("getOverlayInputSelection()");
                String text = result != null ? result.toString() : "";
                if (text.isEmpty()) {
                    return;
                }
                ClipboardContent content = new ClipboardContent();
                content.putString(text);
                Clipboard.getSystemClipboard().setContent(content);
            } catch (Exception ignored) {
                // editor not ready yet
            }
        });
    }

    private void cutOverlayInputSelection() {
        whenReady(() -> {
            try {
                Object result = engine.executeScript("getOverlayInputSelection()");
                String text = result != null ? result.toString() : "";
                if (!text.isEmpty()) {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    Clipboard.getSystemClipboard().setContent(content);
                }
                engine.executeScript("deleteOverlayInputSelection();");
            } catch (Exception ignored) {
                // editor not ready yet
            }
        });
    }

    private void requestEditorFocus() {
        whenReady(() -> engine.executeScript("focusEditor();"));
    }

    public void setOnDirty(Runnable onDirty) {
        this.onDirty = onDirty;
    }

    /** Poll Monaco's isDirty() because WebView javaBridge callbacks are unreliable on some platforms. */
    public void startDirtyPolling() {
        if (dirtyPoller != null) {
            return;
        }
        dirtyPoller = new Timeline(new KeyFrame(Duration.millis(350), event -> pollEditorDirty()));
        dirtyPoller.setCycleCount(Timeline.INDEFINITE);
        dirtyPoller.play();
    }

    public void stopDirtyPolling() {
        if (dirtyPoller != null) {
            dirtyPoller.stop();
            dirtyPoller = null;
        }
    }

    private void pollEditorDirty() {
        if (!ready || onDirty == null) {
            return;
        }
        try {
            Object result = engine.executeScript("typeof isDirty === 'function' && isDirty()");
            boolean dirty = Boolean.TRUE.equals(result);
            if (dirty && !jsDirtyReported) {
                jsDirtyReported = true;
                Platform.runLater(onDirty);
            } else if (!dirty) {
                jsDirtyReported = false;
            }
        } catch (Exception ignored) {
            // editor not ready yet
        }
    }

    private void installBridge() {
        JSObject window = (JSObject) engine.executeScript("window");
        window.setMember("javaBridge", new Object() {
            @SuppressWarnings("unused")
            public void onDirty() {
                if (onDirty != null) {
                    jsDirtyReported = true;
                    Platform.runLater(onDirty);
                }
            }

            @SuppressWarnings("unused")
            public void onEditorReady() {
                jsDirtyReported = false;
            }
        });
    }

    public void openFile(String content, String language) {
        openFile(content, language, false);
    }

    public void openFile(String content, String language, boolean readOnly) {
        whenReady(() -> {
            engine.executeScript(
                    "setContent(" + jsString(content) + "," + jsString(language) + "," + readOnly + ");"
            );
            if (!readOnly) {
                engine.executeScript("focusEditor();");
            }
            jsDirtyReported = false;
        });
    }

    public CompletableFuture<String> getContentAsync() {
        CompletableFuture<String> future = new CompletableFuture<>();
        whenReady(() -> {
            try {
                Object result = engine.executeScript("getContent()");
                future.complete(result != null ? result.toString() : "");
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<String> getSelectionAsync() {
        CompletableFuture<String> future = new CompletableFuture<>();
        whenReady(() -> {
            try {
                Object result = engine.executeScript("getSelection()");
                future.complete(result != null ? result.toString() : "");
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void markClean() {
        whenReady(() -> {
            engine.executeScript("markClean();");
            jsDirtyReported = false;
        });
    }

    private void whenReady(Runnable action) {
        if (ready) {
            Platform.runLater(action);
            return;
        }
        engine.getLoadWorker().stateProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Worker.State> obs,
                                Worker.State old, Worker.State state) {
                if (state == Worker.State.SUCCEEDED) {
                    obs.removeListener(this);
                    Platform.runLater(action);
                }
            }
        });
    }

    static String jsString(String value) {
        if (value == null) {
            return "''";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")
                .replace("\t", "\\t") + "\"";
    }
}

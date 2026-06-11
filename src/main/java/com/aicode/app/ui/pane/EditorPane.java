package com.aicode.app.ui.pane;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.net.URL;
import java.util.concurrent.CompletableFuture;

/** WebView wrapper around Monaco Editor (monaco.html). */
public final class EditorPane extends StackPane {
    private final WebView webView = new WebView();
    private final WebEngine engine = webView.getEngine();
    private volatile boolean ready;
    private Runnable onDirty;

    public EditorPane() {
        getChildren().add(webView);
        webView.setContextMenuEnabled(false);
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

    public void setOnDirty(Runnable onDirty) {
        this.onDirty = onDirty;
    }

    private void installBridge() {
        JSObject window = (JSObject) engine.executeScript("window");
        window.setMember("javaBridge", new Object() {
            @SuppressWarnings("unused")
            public void onDirty() {
                if (onDirty != null) {
                    Platform.runLater(onDirty);
                }
            }
        });
    }

    public void openFile(String content, String language) {
        whenReady(() -> engine.executeScript(
                "setContent(" + jsString(content) + "," + jsString(language) + ");"
        ));
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
        whenReady(() -> engine.executeScript("markClean();"));
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

package com.aicode.app.ui.pane;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls last-modified time for open editor tabs and detects external disk changes.
 */
public final class OpenFileWatcher implements AutoCloseable {
    public interface Listener {
        void onExternalChange(Path path, String diskContent);

        void onExternalDelete(Path path);
    }

    private static final Duration DEFAULT_INTERVAL = Duration.seconds(1.5);

    private final Listener listener;
    private final Map<Path, Long> lastModMillis = new ConcurrentHashMap<>();
    private Timeline timeline;

    public OpenFileWatcher(Listener listener) {
        this.listener = listener;
    }

    public void start() {
        if (timeline != null) {
            return;
        }
        timeline = new Timeline(new KeyFrame(DEFAULT_INTERVAL, event -> poll()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    public void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }

    public void track(Path path) {
        Path key = normalize(path);
        lastModMillis.put(key, readModMillis(key));
    }

    public void untrack(Path path) {
        lastModMillis.remove(normalize(path));
    }

    /** Call after saving or reloading from this app so the same disk revision is not treated as external. */
    public void refreshBaseline(Path path) {
        Path key = normalize(path);
        if (lastModMillis.containsKey(key)) {
            lastModMillis.put(key, readModMillis(key));
        }
    }

    public boolean isTracking(Path path) {
        return lastModMillis.containsKey(normalize(path));
    }

    private void poll() {
        if (lastModMillis.isEmpty()) {
            return;
        }
        for (Map.Entry<Path, Long> entry : Map.copyOf(lastModMillis).entrySet()) {
            Path path = entry.getKey();
            long known = entry.getValue();
            if (!Files.isRegularFile(path)) {
                lastModMillis.remove(path);
                Platform.runLater(() -> listener.onExternalDelete(path));
                continue;
            }
            long current = readModMillis(path);
            if (current == known) {
                continue;
            }
            String content;
            try {
                content = Files.readString(path);
            } catch (IOException e) {
                continue;
            }
            Platform.runLater(() -> listener.onExternalChange(path, content));
        }
    }

    /** Acknowledge the current on-disk revision (after reload or user chose to keep local edits). */
    public void acknowledgeDiskRevision(Path path) {
        Path key = normalize(path);
        if (lastModMillis.containsKey(key)) {
            lastModMillis.put(key, readModMillis(key));
        }
    }

    private static long readModMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return -1L;
        }
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    @Override
    public void close() {
        stop();
        lastModMillis.clear();
    }
}

package com.aicode.app.ui;

import javafx.animation.AnimationTimer;
import javafx.application.Platform;

/**
 * Reveals streamed LLM tokens on the JavaFX thread frame-by-frame.
 * Even when the API delivers a full response in one burst, text drips out
 * progressively instead of appearing all at once.
 */
public final class StreamingChatAppender {
    private static final int BASE_CHARS_PER_FRAME = 10;

    private final ChatTranscriptView target;
    private final StringBuilder pending = new StringBuilder();
    private final AnimationTimer dripTimer = new AnimationTimer() {
        @Override
        public void handle(long now) {
            dripFrame();
        }
    };
    private volatile boolean streaming;
    private volatile boolean timerActive;
    private Runnable drainCallback;

    public StreamingChatAppender(ChatTranscriptView target) {
        this.target = target;
    }

    /** Call when a new assistant reply begins. */
    public void beginStream() {
        Platform.runLater(() -> {
            streaming = true;
            drainCallback = null;
            target.beginAssistantStream();
            startTimer();
        });
    }

    /** Safe to call from any thread while the LLM is streaming. */
    public void append(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        synchronized (pending) {
            pending.append(text);
        }
        Platform.runLater(this::ensureTimerRunning);
    }

    /** Clears undrained tokens when switching away from the streaming conversation. */
    public void resetPending() {
        Platform.runLater(() -> {
            synchronized (pending) {
                pending.setLength(0);
            }
            streaming = false;
            drainCallback = null;
            target.endAssistantStream();
            stopTimer();
        });
    }

    /** Marks the stream complete; {@code onDrained} runs after all pending text is shown. */
    public void finishStream(Runnable onDrained) {
        Platform.runLater(() -> {
            streaming = false;
            if (!hasPending()) {
                stopTimer();
                target.endAssistantStream();
                if (onDrained != null) {
                    onDrained.run();
                }
            } else {
                drainCallback = onDrained;
            }
        });
    }

    private void ensureTimerRunning() {
        startTimer();
    }

    private void dripFrame() {
        String chunk;
        synchronized (pending) {
            if (pending.isEmpty()) {
                if (!streaming) {
                    stopTimer();
                    target.endAssistantStream();
                    Runnable callback = drainCallback;
                    drainCallback = null;
                    if (callback != null) {
                        callback.run();
                    }
                }
                return;
            }
            int count = Math.min(charsThisFrame(pending.length()), pending.length());
            chunk = pending.substring(0, count);
            pending.delete(0, count);
        }
        target.appendStreamChunk(chunk);
    }

    private boolean hasPending() {
        synchronized (pending) {
            return !pending.isEmpty();
        }
    }

    private void startTimer() {
        if (!timerActive) {
            timerActive = true;
            dripTimer.start();
        }
    }

    private void stopTimer() {
        if (timerActive) {
            timerActive = false;
            dripTimer.stop();
        }
    }

    private static int charsThisFrame(int backlog) {
        if (backlog > 800) {
            return 64;
        }
        if (backlog > 300) {
            return 32;
        }
        if (backlog > 80) {
            return 18;
        }
        return BASE_CHARS_PER_FRAME;
    }
}

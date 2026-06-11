package com.aicode.app.session;

import java.util.concurrent.atomic.AtomicBoolean;

/** Cooperative cancellation flag for an in-flight agent or chat request. */
public final class RunCancellation {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile String partialText = "";

    public void cancel() {
        cancelled.set(true);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public void trackPartialText(String text) {
        if (text != null && !text.isEmpty()) {
            partialText = text;
        }
    }

    public String partialText() {
        return partialText;
    }
}

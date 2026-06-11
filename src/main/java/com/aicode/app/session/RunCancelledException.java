package com.aicode.app.session;

/** Raised when a run is stopped by the user. */
public final class RunCancelledException extends RuntimeException {
    private final String partialText;

    public RunCancelledException(String partialText) {
        super("Run cancelled");
        this.partialText = partialText != null ? partialText : "";
    }

    public String partialText() {
        return partialText;
    }
}

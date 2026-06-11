package com.aicode.agent.llm;

/**
 * HTTP error from an LLM API call, carrying status code for retry logic.
 */
public class LLMHttpException extends RuntimeException {
    private final int statusCode;

    public LLMHttpException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }

    public int getStatus() {
        return statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

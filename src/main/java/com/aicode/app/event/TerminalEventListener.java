package com.aicode.app.event;

import com.aicode.agent.Markdown;
import com.aicode.agent.ToolDisplay;

/**
 * Renders agent events to the terminal (CLI mode).
 */
public final class TerminalEventListener implements AgentEventListener {
    private final ToolDisplay.Spinner spinner = new ToolDisplay.Spinner("");
    private boolean streamingLine;

    @Override
    public void onEvent(AgentEvent event) {
        switch (event) {
            case AgentEvent.ToolCallStarted e -> {
                spinner.update(e.toolName() + "...");
                spinner.start();
            }
            case AgentEvent.ToolCallFinished e -> {
                spinner.succeed(e.toolName() + " [" + e.durationMs() + "ms]");
            }
            case AgentEvent.TextDelta e -> {
                if (!streamingLine) {
                    streamingLine = true;
                }
                System.out.print(e.delta());
                System.out.flush();
            }
            case AgentEvent.TextDone e -> {
                if (streamingLine) {
                    System.out.println();
                    streamingLine = false;
                }
            }
            case AgentEvent.ApprovalRequired e -> {
                System.err.println(Markdown.YELLOW + "⚠ Approval required: " + e.reason() + Markdown.RESET);
                System.err.println("  Tool: " + e.toolName());
                System.err.println("  Input: " + e.input());
            }
            case AgentEvent.Error e -> spinner.fail(e.message());
            case AgentEvent.OutputTruncated e -> {
                String prefix = e.retrying() ? "↻ " : "⚠ ";
                if (streamingLine) {
                    System.out.println();
                    streamingLine = false;
                }
                System.err.println(Markdown.YELLOW + prefix + e.message() + Markdown.RESET);
            }
            default -> {
                // Done, ApprovalResolved — no terminal output
            }
        }
    }
}

package com.aicode.app.ui;

import com.aicode.app.event.AgentEvent;
import com.aicode.app.event.AgentEventListener;
import com.aicode.app.session.FileEditProposal;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class UiAgentBridge implements AgentEventListener {
    private final Consumer<String> streamAppender;
    private final Consumer<String> activityAppender;
    private final BiConsumer<AgentEvent.ApprovalRequired, Runnable> approvalHandler;
    private final Consumer<FileEditProposal> fileEditHandler;
    private final Consumer<Path> onFileDiskChanged;
    private final StringBuilder streaming = new StringBuilder();

    public UiAgentBridge(
            Consumer<String> streamAppender,
            Consumer<String> activityAppender,
            BiConsumer<AgentEvent.ApprovalRequired, Runnable> approvalHandler
    ) {
        this(streamAppender, activityAppender, approvalHandler, null, null);
    }

    public UiAgentBridge(
            Consumer<String> streamAppender,
            Consumer<String> activityAppender,
            BiConsumer<AgentEvent.ApprovalRequired, Runnable> approvalHandler,
            Consumer<FileEditProposal> fileEditHandler
    ) {
        this(streamAppender, activityAppender, approvalHandler, fileEditHandler, null);
    }

    public UiAgentBridge(
            Consumer<String> streamAppender,
            Consumer<String> activityAppender,
            BiConsumer<AgentEvent.ApprovalRequired, Runnable> approvalHandler,
            Consumer<FileEditProposal> fileEditHandler,
            Consumer<Path> onFileDiskChanged
    ) {
        this.streamAppender = streamAppender;
        this.activityAppender = activityAppender;
        this.approvalHandler = approvalHandler;
        this.fileEditHandler = fileEditHandler;
        this.onFileDiskChanged = onFileDiskChanged;
    }

    @Override
    public void onEvent(AgentEvent event) {
        switch (event) {
            case AgentEvent.TextDelta e -> {
                streaming.append(e.delta());
                streamAppender.accept(e.delta());
            }
            case AgentEvent.TextDone e -> {
                if (!streaming.isEmpty()) {
                    streamAppender.accept("\n");
                    streaming.setLength(0);
                }
            }
            case AgentEvent.ToolCallStarted e ->
                    activityAppender.accept("▶ " + e.toolName() + " " + summarizeInput(e.input()));
            case AgentEvent.ToolCallFinished e ->
                    activityAppender.accept("✔ " + e.toolName() + " (" + e.durationMs() + "ms)");
            case AgentEvent.FileEditProposed e -> {
                activityAppender.accept("📝 " + Path.of(e.filePath()).getFileName());
                if (onFileDiskChanged != null) {
                    onFileDiskChanged.accept(Path.of(e.filePath()));
                }
                if (e.requiresReview() && fileEditHandler != null) {
                    fileEditHandler.accept(new FileEditProposal(
                            e.editId(),
                            Path.of(e.filePath()),
                            e.oldContent(),
                            e.newContent(),
                            e.created()
                    ));
                }
            }
            case AgentEvent.ApprovalRequired e ->
                    approvalHandler.accept(e, () -> {});
            case AgentEvent.Error e -> activityAppender.accept("✖ " + e.message());
            case AgentEvent.OutputTruncated e -> {
                String prefix = e.retrying() ? "↻ " : "⚠ ";
                activityAppender.accept(prefix + e.message());
            }
            case AgentEvent.Cancelled e -> activityAppender.accept("[已停止]");
            default -> {
                // Done, ApprovalResolved
            }
        }
    }

    private static String summarizeInput(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String oneLine = input.toString().replace('\n', ' ');
        return oneLine.length() > 120 ? oneLine.substring(0, 120) + "…" : oneLine;
    }
}

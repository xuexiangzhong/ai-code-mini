package com.aicode.app.ui;

import com.aicode.app.session.FileEditProposal;
import com.aicode.app.ui.dialog.ActivityLogDialog;
import com.aicode.app.ui.dialog.FileEditDiffDialog;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Scrollable chat area with styled user / assistant bubbles and a single activity strip per turn. */
public final class ChatTranscriptView extends ScrollPane {
    private static final double LOAD_OLDER_THRESHOLD = 0.08;

    private final VBox messageBox = new VBox(10);
    private TextArea streamingBody;
    private TurnUi currentTurn;
    private Runnable onLoadOlder;
    private boolean hasOlderTurns;
    private boolean loadingOlderTurns;
    private BiConsumer<String, Boolean> onFileEditResolved = (id, kept) -> {};
    private Consumer<Path> onFileEditChanged = path -> {};
    private final Map<String, FileEditCardUi> pendingFileEdits = new LinkedHashMap<>();

    public ChatTranscriptView() {
        getStyleClass().add("chat-transcript");
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        messageBox.getStyleClass().add("chat-transcript-inner");
        messageBox.setFillWidth(true);
        messageBox.setPadding(new Insets(6, 4, 12, 4));
        setContent(messageBox);
        vvalueProperty().addListener((obs, oldValue, newValue) -> maybeLoadOlder(newValue.doubleValue()));
    }

    public void setOnFileEditResolved(BiConsumer<String, Boolean> onFileEditResolved) {
        this.onFileEditResolved = onFileEditResolved != null ? onFileEditResolved : (id, kept) -> {};
    }

    public void setOnFileEditChanged(Consumer<Path> onFileEditChanged) {
        this.onFileEditChanged = onFileEditChanged != null ? onFileEditChanged : path -> {};
    }

    /** Inline Cursor-style card: compact accept/reject in the chat stream. */
    public void showFileEditReview(FileEditProposal proposal) {
        if (proposal == null) {
            return;
        }
        Platform.runLater(() -> {
            ensureCurrentTurn();
            FileEditCardUi card = buildFileEditCard(proposal);
            pendingFileEdits.put(proposal.id(), card);
            insertFileEditCard(card.root());
            scrollToBottom();
        });
    }

    public void setOnLoadOlder(Runnable onLoadOlder) {
        this.onLoadOlder = onLoadOlder;
    }

    public void setHasOlderTurns(boolean hasOlderTurns) {
        this.hasOlderTurns = hasOlderTurns;
    }

    public void finishLoadingOlderTurns() {
        loadingOlderTurns = false;
    }

    public void clear() {
        messageBox.getChildren().clear();
        streamingBody = null;
        currentTurn = null;
        pendingFileEdits.clear();
    }

    public void loadTurns(List<ChatTurn> turns) {
        clear();
        for (ChatTurn turn : turns) {
            renderTurn(turn);
        }
        scrollToBottom();
    }

    public void prependTurns(List<ChatTurn> turns, boolean hasOlder) {
        if (turns == null || turns.isEmpty()) {
            setHasOlderTurns(hasOlder);
            return;
        }
        double oldHeight = contentHeight();
        double oldScrollTop = getVvalue() * Math.max(1.0, oldHeight - getViewportBounds().getHeight());

        for (int i = turns.size() - 1; i >= 0; i--) {
            TurnUi ui = new TurnUi(new ArrayList<>(turns.get(i).activities()));
            VBox turnBox = buildTurnShell(ui, turns.get(i).userText(), turns.get(i).createdAt());
            if (!turns.get(i).assistantText().isEmpty()) {
                ui.assistantBody = attachAssistantBlock(turnBox);
                ui.assistantBody.setText(turns.get(i).assistantText());
            }
            refreshActivityRow(ui);
            messageBox.getChildren().addFirst(turnBox);
        }

        layout();
        double newHeight = contentHeight();
        double delta = newHeight - oldHeight;
        double viewport = getViewportBounds().getHeight();
        if (delta > 0 && newHeight > viewport) {
            setVvalue(Math.min(1.0, (oldScrollTop + delta) / (newHeight - viewport)));
        }
        setHasOlderTurns(hasOlder);
    }

    private double contentHeight() {
        return messageBox.getBoundsInParent().getHeight();
    }

    private void maybeLoadOlder(double vvalue) {
        if (onLoadOlder == null || loadingOlderTurns || !hasOlderTurns || vvalue > LOAD_OLDER_THRESHOLD) {
            return;
        }
        loadingOlderTurns = true;
        onLoadOlder.run();
    }

    private void ensureCurrentTurn() {
        if (currentTurn == null) {
            startTurn(null);
        }
    }

    private void insertFileEditCard(VBox card) {
        VBox turnBox = currentTurn.turnBox;
        if (currentTurn.fileEditHost == null) {
            currentTurn.fileEditHost = new VBox(6);
            currentTurn.fileEditHost.getStyleClass().add("chat-file-edit-host");
            turnBox.getChildren().add(currentTurn.fileEditHost);
        } else {
            // Keep pending review cards below assistant text and other turn content.
            turnBox.getChildren().remove(currentTurn.fileEditHost);
            turnBox.getChildren().add(currentTurn.fileEditHost);
        }
        currentTurn.fileEditHost.getChildren().add(card);
    }

    private FileEditCardUi buildFileEditCard(FileEditProposal proposal) {
        Label title = new Label("📝 " + proposal.summary());
        title.getStyleClass().add("chat-file-edit-title");
        title.setMaxWidth(Double.MAX_VALUE);

        Button accept = new Button("接受");
        Button reject = new Button("拒绝");
        accept.getStyleClass().addAll("chat-file-edit-btn", "chat-file-edit-accept");
        reject.getStyleClass().addAll("chat-file-edit-btn", "chat-file-edit-reject");

        Button details = new Button("改动");
        details.getStyleClass().addAll("chat-file-edit-btn", "chat-file-edit-details");
        details.setOnAction(e -> {
            Stage owner = getScene() != null ? (Stage) getScene().getWindow() : null;
            FileEditDiffDialog.show(owner, proposal);
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox actions = new HBox(6, accept, reject, details, spacer);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(4, title, actions);
        card.getStyleClass().add("chat-file-edit-card");
        card.setMaxWidth(Double.MAX_VALUE);

        HBox row = new HBox(card);
        row.getStyleClass().add("chat-row-file-edit");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(card, Priority.ALWAYS);

        VBox root = new VBox(row);

        accept.setOnAction(e -> resolveFileEdit(proposal, card, title, actions, true));
        reject.setOnAction(e -> resolveFileEdit(proposal, card, title, actions, false));

        return new FileEditCardUi(root, card, title, actions);
    }

    private void resolveFileEdit(
            FileEditProposal proposal,
            VBox card,
            Label title,
            HBox actions,
            boolean kept
    ) {
        onFileEditResolved.accept(proposal.id(), kept);
        pendingFileEdits.remove(proposal.id());
        card.getStyleClass().add("chat-file-edit-card-resolved");
        title.setText((kept ? "✓ " : "✗ ") + proposal.summary() + (kept ? " · 已保留" : " · 已撤销"));
        actions.setVisible(false);
        actions.setManaged(false);
        onFileEditChanged.accept(proposal.filePath());
        scrollToBottom();
    }

    private void startTurn(String userText) {
        startTurn(userText, java.time.Instant.now().toString());
    }

    public void startTurn(String userText, String createdAt) {
        currentTurn = new TurnUi(new ArrayList<>());
        VBox turnBox = buildTurnShell(currentTurn, userText, createdAt);
        messageBox.getChildren().add(turnBox);
        scrollToBottom();
    }

    public void beginAssistantStream() {
        if (currentTurn == null) {
            startTurn(null);
        }
        if (currentTurn.assistantBody == null) {
            currentTurn.assistantBody = attachAssistantBlock(currentTurn.turnBox);
        }
        streamingBody = currentTurn.assistantBody;
        scrollToBottom();
    }

    public void appendStreamChunk(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (streamingBody == null) {
            beginAssistantStream();
        }
        streamingBody.appendText(text);
        adjustMessageHeight(streamingBody);
        scrollToBottom();
    }

    public void endAssistantStream() {
        streamingBody = null;
    }

    public void appendAssistant(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (streamingBody != null) {
            streamingBody.appendText(text);
            adjustMessageHeight(streamingBody);
        } else {
            beginAssistantStream();
            streamingBody.appendText(text);
            adjustMessageHeight(streamingBody);
        }
        scrollToBottom();
    }

    public void updateCurrentTurnActivity(List<String> activities) {
        if (activities == null || activities.isEmpty()) {
            return;
        }
        if (currentTurn == null) {
            startTurn(null);
        }
        currentTurn.activities.clear();
        currentTurn.activities.addAll(activities);
        refreshActivityRow(currentTurn);
        scrollToBottom();
    }

    public void appendStandaloneNotice(String text) {
        appendStandaloneNotice(text, java.time.Instant.now().toString());
    }

    public void appendStandaloneNotice(String text, String createdAt) {
        TurnUi notice = new TurnUi(List.of(text.strip()));
        messageBox.getChildren().add(buildTurnShell(notice, null, createdAt));
        refreshActivityRow(notice);
        scrollToBottom();
    }

    public void showPlainError(String text) {
        clear();
        appendStandaloneNotice(text);
    }

    private void renderTurn(ChatTurn turn) {
        TurnUi ui = new TurnUi(new ArrayList<>(turn.activities()));
        VBox turnBox = buildTurnShell(ui, turn.userText(), turn.createdAt());
        if (!turn.assistantText().isEmpty()) {
            ui.assistantBody = attachAssistantBlock(turnBox);
            ui.assistantBody.setText(turn.assistantText());
        }
        refreshActivityRow(ui);
        messageBox.getChildren().add(turnBox);
    }

    private VBox buildTurnShell(TurnUi ui, String userText, String createdAt) {
        VBox turnBox = new VBox(8);
        turnBox.getStyleClass().add("chat-turn");
        turnBox.setFillWidth(true);
        ui.turnBox = turnBox;

        String timeLabel = TurnTimeFormat.display(createdAt);
        if (!timeLabel.isEmpty()) {
            Label time = new Label(timeLabel);
            time.getStyleClass().add("chat-turn-time");
            HBox timeRow = new HBox(time);
            timeRow.setAlignment(Pos.CENTER_RIGHT);
            turnBox.getChildren().add(timeRow);
        }

        if (userText != null && !userText.isBlank()) {
            turnBox.getChildren().add(createUserBubble(userText));
        }

        ui.activityRow = createActivityRow(ui);
        turnBox.getChildren().add(ui.activityRow);
        return turnBox;
    }

    private void refreshActivityRow(TurnUi ui) {
        if (ui.activities.isEmpty()) {
            ui.activityRow.setVisible(false);
            ui.activityRow.setManaged(false);
            return;
        }
        ui.activityRow.setVisible(true);
        ui.activityRow.setManaged(true);
        ui.previewLabel.setText(previewLine(ui.activities.getLast()));
        int count = ui.activities.size();
        ui.countLabel.setText(count > 1 ? count + " 步" : "");
        ui.countLabel.setVisible(count > 1);
        ui.countLabel.setManaged(count > 1);
    }

    private HBox createUserBubble(String text) {
        TextArea body = createMessageBody(text);

        VBox bubble = new VBox(body);
        bubble.getStyleClass().add("chat-bubble-user");
        bubble.setMaxWidth(Region.USE_PREF_SIZE);

        HBox row = new HBox(bubble);
        row.getStyleClass().add("chat-row-user");
        row.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(row, Priority.ALWAYS);
        return row;
    }

    private TextArea attachAssistantBlock(VBox turnBox) {
        TextArea body = createMessageBody("");

        VBox bubble = new VBox(body);
        bubble.getStyleClass().add("chat-bubble-assistant");
        bubble.setMaxWidth(Region.USE_PREF_SIZE);

        HBox row = new HBox(bubble);
        row.getStyleClass().add("chat-row-assistant");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(row, Priority.ALWAYS);
        if (currentTurn != null && currentTurn.fileEditHost != null
                && turnBox.getChildren().contains(currentTurn.fileEditHost)) {
            int idx = turnBox.getChildren().indexOf(currentTurn.fileEditHost);
            turnBox.getChildren().add(idx, row);
        } else {
            turnBox.getChildren().add(row);
        }
        return body;
    }

    private HBox createActivityRow(TurnUi ui) {
        Button icon = new Button("☰");
        icon.getStyleClass().addAll("chat-activity-icon");
        icon.setFocusTraversable(false);
        icon.setTooltip(new javafx.scene.control.Tooltip("查看全部活动"));

        ui.previewLabel = new Label("");
        ui.previewLabel.getStyleClass().add("chat-activity-preview");
        ui.previewLabel.setWrapText(false);
        ui.previewLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(ui.previewLabel, Priority.ALWAYS);

        ui.countLabel = new Label("");
        ui.countLabel.getStyleClass().add("chat-activity-count");

        HBox row = new HBox(8, icon, ui.previewLabel, ui.countLabel);
        row.getStyleClass().add("chat-activity-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setVisible(false);
        row.setManaged(false);

        Runnable openLog = () -> {
            Stage owner = getScene() != null ? (Stage) getScene().getWindow() : null;
            ActivityLogDialog.show(owner, ui.activities);
        };
        icon.setOnAction(e -> openLog.run());
        row.setOnMouseClicked(e -> {
            if (e.getTarget() != icon) {
                openLog.run();
            }
        });
        return row;
    }

    private void scrollToBottom() {
        layout();
        setVvalue(1.0);
    }

    private static TextArea createMessageBody(String text) {
        TextArea body = new TextArea(text == null ? "" : text);
        body.getStyleClass().add("chat-bubble-body");
        body.setEditable(false);
        body.setWrapText(true);
        body.setMaxWidth(Region.USE_PREF_SIZE);
        body.setPrefColumnCount(28);
        adjustMessageHeight(body);
        body.textProperty().addListener((obs, old, val) -> adjustMessageHeight(body));
        installCopyContextMenu(body);
        return body;
    }

    private static void installCopyContextMenu(TextArea body) {
        MenuItem copy = new MenuItem("复制");
        copy.setOnAction(e -> {
            String selected = body.getSelectedText();
            if (selected != null && !selected.isEmpty()) {
                body.copy();
            } else {
                body.selectAll();
                body.copy();
                body.deselect();
            }
        });
        body.setContextMenu(new ContextMenu(copy));
    }

    /** Estimate visible rows so the bubble grows with content without an inner scrollbar. */
    private static void adjustMessageHeight(TextArea body) {
        String text = body.getText();
        if (text == null || text.isEmpty()) {
            body.setPrefRowCount(1);
            return;
        }
        int rows = 0;
        for (String line : text.split("\n", -1)) {
            rows += Math.max(1, (line.length() + 41) / 42);
        }
        body.setPrefRowCount(Math.min(60, Math.max(1, rows)));
    }

    private static String previewLine(String text) {
        String oneLine = text.replace('\n', ' ').strip();
        if (oneLine.length() <= 72) {
            return oneLine;
        }
        return oneLine.substring(0, 69) + "…";
    }

    private static final class TurnUi {
        private final List<String> activities;
        private VBox turnBox;
        private HBox activityRow;
        private Label previewLabel;
        private Label countLabel;
        private TextArea assistantBody;
        private VBox fileEditHost;

        private TurnUi(List<String> activities) {
            this.activities = new ArrayList<>(activities);
        }
    }

    private record FileEditCardUi(VBox root, VBox card, Label title, HBox actions) {}
}

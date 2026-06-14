package com.aicode.app.ui;

import com.aicode.app.ui.dialog.ActivityLogDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

/** Scrollable chat area with styled user / assistant bubbles and a single activity strip per turn. */
public final class ChatTranscriptView extends ScrollPane {
    private static final double LOAD_OLDER_THRESHOLD = 0.08;

    private final VBox messageBox = new VBox(10);
    private Label streamingBody;
    private TurnUi currentTurn;
    private Runnable onLoadOlder;
    private boolean hasOlderTurns;
    private boolean loadingOlderTurns;

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

    public void startTurn(String userText) {
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
        streamingBody.setText(streamingBody.getText() + text);
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
            streamingBody.setText(streamingBody.getText() + text);
        } else {
            beginAssistantStream();
            streamingBody.setText(streamingBody.getText() + text);
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
        Label body = new Label(text);
        body.getStyleClass().add("chat-bubble-body");
        body.setWrapText(true);
        body.setMaxWidth(Region.USE_PREF_SIZE);

        VBox bubble = new VBox(body);
        bubble.getStyleClass().add("chat-bubble-user");
        bubble.setMaxWidth(Region.USE_PREF_SIZE);

        HBox row = new HBox(bubble);
        row.getStyleClass().add("chat-row-user");
        row.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(row, Priority.ALWAYS);
        return row;
    }

    private Label attachAssistantBlock(VBox turnBox) {
        Label body = new Label("");
        body.getStyleClass().add("chat-bubble-body");
        body.setWrapText(true);
        body.setMaxWidth(Region.USE_PREF_SIZE);

        VBox bubble = new VBox(body);
        bubble.getStyleClass().add("chat-bubble-assistant");
        bubble.setMaxWidth(Region.USE_PREF_SIZE);

        HBox row = new HBox(bubble);
        row.getStyleClass().add("chat-row-assistant");
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(row, Priority.ALWAYS);
        turnBox.getChildren().add(row);
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
        private Label assistantBody;

        private TurnUi(List<String> activities) {
            this.activities = new ArrayList<>(activities);
        }
    }
}

package com.aicode.app.ui;

import com.aicode.app.session.FileEditProposal;
import com.aicode.app.session.FileEditRevertor;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Review AI file edits with side-by-side diff and accept/reject actions. */
public final class DiffReviewPanel extends VBox {
    private final ListView<FileEditProposal> listView = new ListView<>();
    private final TextArea oldView = new TextArea();
    private final TextArea newView = new TextArea();
    private final Label headerLabel = new Label("AI 文件改动");
    private final Map<String, FileEditProposal> pending = new LinkedHashMap<>();
    private Consumer<Path> onFileChanged = path -> {};

    public DiffReviewPanel() {
        getStyleClass().add("diff-review-panel");
        setSpacing(6);
        setPadding(new Insets(6, 8, 6, 8));
        setVisible(false);
        setManaged(false);

        headerLabel.getStyleClass().add("diff-review-title");
        listView.getStyleClass().add("diff-review-list");
        listView.setPrefHeight(56);
        listView.setMaxHeight(72);
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(FileEditProposal item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.summary());
            }
        });
        listView.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> showProposal(selected));

        oldView.setEditable(false);
        newView.setEditable(false);
        oldView.getStyleClass().add("diff-review-text");
        newView.getStyleClass().add("diff-review-text");
        oldView.setPrefRowCount(4);
        newView.setPrefRowCount(4);

        SplitPane split = new SplitPane(oldView, newView);
        split.setDividerPositions(0.5);
        split.setPrefHeight(120);
        VBox.setVgrow(split, Priority.ALWAYS);

        Button acceptButton = new Button("接受");
        Button rejectButton = new Button("拒绝并撤销");
        acceptButton.getStyleClass().add("ghost-button");
        rejectButton.getStyleClass().add("ghost-button");
        acceptButton.setOnAction(e -> acceptSelected());
        rejectButton.setOnAction(e -> rejectSelected());

        HBox actions = new HBox(8, acceptButton, rejectButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(headerLabel, listView, split, actions);
    }

    public void setOnFileChanged(Consumer<Path> onFileChanged) {
        this.onFileChanged = onFileChanged != null ? onFileChanged : path -> {};
    }

    public void propose(FileEditProposal proposal) {
        Platform.runLater(() -> {
            pending.put(proposal.id(), proposal);
            refreshList();
            listView.getSelectionModel().select(proposal);
            setVisible(true);
            setManaged(true);
        });
    }

    private void acceptSelected() {
        FileEditProposal selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        pending.remove(selected.id());
        refreshList();
        onFileChanged.accept(selected.filePath());
    }

    private void rejectSelected() {
        FileEditProposal selected = listView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            FileEditRevertor.revert(selected);
            pending.remove(selected.id());
            refreshList();
            onFileChanged.accept(selected.filePath());
        } catch (IOException e) {
            headerLabel.setText("撤销失败: " + e.getMessage());
        }
    }

    private void showProposal(FileEditProposal proposal) {
        if (proposal == null) {
            oldView.clear();
            newView.clear();
            return;
        }
        oldView.setText(proposal.created() ? "(新文件)" : nullToEmpty(proposal.oldContent()));
        newView.setText(nullToEmpty(proposal.newContent()));
    }

    private void refreshList() {
        listView.getItems().setAll(pending.values());
        headerLabel.setText(pending.isEmpty() ? "AI 文件改动" : "AI 文件改动 (" + pending.size() + ")");
        if (pending.isEmpty()) {
            oldView.clear();
            newView.clear();
            setVisible(false);
            setManaged(false);
        }
    }

    private static String nullToEmpty(String text) {
        return text != null ? text : "";
    }
}

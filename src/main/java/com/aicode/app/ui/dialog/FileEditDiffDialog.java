package com.aicode.app.ui.dialog;

import com.aicode.app.session.FileEditProposal;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Side-by-side diff view (Git-style) for an inline file-edit card. */
public final class FileEditDiffDialog {
    private FileEditDiffDialog() {}

    public static void show(Stage owner, FileEditProposal proposal) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.NONE);
        dialog.setTitle(proposal.summary());

        Label title = new Label(proposal.summary());
        title.getStyleClass().add("file-edit-diff-title");

        TextArea oldView = createDiffPane(
                proposal.created() ? "(新文件)" : nullToEmpty(proposal.oldContent())
        );
        TextArea newView = createDiffPane(nullToEmpty(proposal.newContent()));
        bindScrollSync(oldView, newView);

        VBox oldPane = labeledPane("修改前", oldView);
        VBox newPane = labeledPane("修改后", newView);

        SplitPane split = new SplitPane(oldPane, newPane);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.5);
        split.getStyleClass().add("file-edit-diff-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        Button close = new Button("关闭");
        close.getStyleClass().add("ghost-button");
        close.setOnAction(e -> dialog.close());

        HBox footer = new HBox(close);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(10, title, split, footer);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("file-edit-diff-dialog");
        if (isAgentShell(owner)) {
            root.getStyleClass().add("file-edit-diff-dialog-light");
        }

        Scene scene = new Scene(root, 920, 560);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        dialog.setScene(scene);
        dialog.show();
    }

    private static VBox labeledPane(String sideTitle, TextArea body) {
        Label label = new Label(sideTitle);
        label.getStyleClass().add("file-edit-diff-side-title");
        VBox pane = new VBox(6, label, body);
        pane.getStyleClass().add("file-edit-diff-side");
        VBox.setVgrow(body, Priority.ALWAYS);
        return pane;
    }

    private static TextArea createDiffPane(String text) {
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(false);
        area.getStyleClass().add("file-edit-diff-body");
        return area;
    }

    private static void bindScrollSync(TextArea left, TextArea right) {
        final boolean[] syncing = {false};
        left.scrollTopProperty().addListener((obs, oldValue, newValue) -> {
            if (syncing[0]) {
                return;
            }
            syncing[0] = true;
            right.setScrollTop(newValue.doubleValue());
            syncing[0] = false;
        });
        right.scrollTopProperty().addListener((obs, oldValue, newValue) -> {
            if (syncing[0]) {
                return;
            }
            syncing[0] = true;
            left.setScrollTop(newValue.doubleValue());
            syncing[0] = false;
        });
    }

    private static String nullToEmpty(String text) {
        return text != null ? text : "";
    }

    private static boolean isAgentShell(Stage owner) {
        if (owner == null || owner.getScene() == null) {
            return false;
        }
        return owner.getScene().getRoot().getStyleClass().contains("agent-shell");
    }
}

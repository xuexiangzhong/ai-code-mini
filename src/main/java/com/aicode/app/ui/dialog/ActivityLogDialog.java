package com.aicode.app.ui.dialog;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

public final class ActivityLogDialog {
    private ActivityLogDialog() {}

    public static void show(Stage owner, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.NONE);
        dialog.setTitle("活动记录");

        Label title = new Label("共 " + lines.size() + " 条活动");
        title.getStyleClass().add("activity-log-title");

        TextArea body = new TextArea(String.join("\n", lines));
        body.setEditable(false);
        body.setWrapText(true);
        body.getStyleClass().add("activity-log-body");

        Button close = new Button("关闭");
        close.getStyleClass().add("ghost-button");
        close.setOnAction(e -> dialog.close());

        VBox root = new VBox(10, title, body, close);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("activity-log-dialog");
        Scene scene = new Scene(root, 520, 360);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        }
        dialog.setScene(scene);
        dialog.show();
    }
}

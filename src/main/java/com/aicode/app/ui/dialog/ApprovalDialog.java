package com.aicode.app.ui.dialog;

import com.aicode.app.event.AgentEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.function.Consumer;

public final class ApprovalDialog {
    private final Stage dialog;

    public ApprovalDialog(Stage owner, AgentEvent.ApprovalRequired event, Consumer<Boolean> onResult) {
        dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Approval Required");

        Label reason = new Label(event.reason());
        reason.setWrapText(true);
        Label tool = new Label("Tool: " + event.toolName());
        Label input = new Label("Input: " + event.input());
        input.setWrapText(true);

        Button approve = new Button("Approve");
        Button reject = new Button("Reject");
        approve.setOnAction(e -> {
            onResult.accept(true);
            dialog.close();
        });
        reject.setOnAction(e -> {
            onResult.accept(false);
            dialog.close();
        });

        HBox buttons = new HBox(10, approve, reject);
        VBox root = new VBox(10, reason, tool, input, buttons);
        root.setPadding(new Insets(16));
        dialog.setScene(new Scene(root, 420, 220));
    }

    public void show() {
        dialog.showAndWait();
    }
}

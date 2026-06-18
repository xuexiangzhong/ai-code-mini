package com.aicode.app.ui.dialog;

import com.aicode.app.config.WorkingDirectory;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.function.Consumer;

/** Prompt when an open tab's file changed on disk while the editor has unsaved edits. */
public final class ExternalFileChangeDialog {
    private ExternalFileChangeDialog() {}

    public static void show(Stage owner, Path filePath, Consumer<Boolean> onReloadChosen) {
        Stage dialog = new Stage();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("文件已在磁盘上更改");

        Label headline = new Label("\"" + WorkingDirectory.displayName(filePath) + "\" 已被外部程序修改。");
        headline.setWrapText(true);
        Label hint = new Label("是否重新加载磁盘内容？选择「保留编辑」将保留当前未保存的修改。");
        hint.setWrapText(true);
        hint.getStyleClass().add("muted-label");

        Button reload = new Button("重新加载");
        reload.setDefaultButton(true);
        Button keep = new Button("保留编辑");
        keep.setCancelButton(true);

        reload.setOnAction(e -> {
            onReloadChosen.accept(true);
            dialog.close();
        });
        keep.setOnAction(e -> {
            onReloadChosen.accept(false);
            dialog.close();
        });

        HBox buttons = new HBox(10, reload, keep);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        VBox root = new VBox(8, headline, hint, buttons);
        root.setPadding(new Insets(16));
        VBox.setVgrow(hint, Priority.ALWAYS);
        dialog.setScene(new Scene(root, 460, 160));
        dialog.showAndWait();
    }
}

package com.aicode.app.ui.pane;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bottom panel: interactive shell + tool/agent output log. */
public final class TerminalPane extends VBox {
    private final TextArea shellOutput = new TextArea();
    private final TextField shellInput = new TextField();
    private final TextArea toolOutput = new TextArea();
    private Process shellProcess;
    private OutputStream shellIn;
    private final ExecutorService readerPool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "shell-reader");
        t.setDaemon(true);
        return t;
    });

    public TerminalPane() {
        getStyleClass().add("terminal-pane");
        setSpacing(0);

        Tab shellTab = new Tab("终端", buildShellPane());
        shellTab.setClosable(false);
        Tab outputTab = new Tab("工具输出", buildOutputPane());
        outputTab.setClosable(false);

        TabPane tabs = new TabPane(shellTab, outputTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getStyleClass().add("terminal-tabs");
        VBox.setVgrow(tabs, Priority.ALWAYS);
        getChildren().add(tabs);
    }

    public void startShell(Path workspace) {
        stopShell();
        shellOutput.clear();
        appendShell("AiCode 终端 — 工作区: " + workspace + "\n");
        try {
            String shell = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "cmd.exe" : "/bin/bash";
            ProcessBuilder pb = new ProcessBuilder(shell);
            pb.directory(workspace.toFile());
            pb.redirectErrorStream(true);
            shellProcess = pb.start();
            shellIn = shellProcess.getOutputStream();
            readerPool.submit(() -> readShellStream(shellProcess));
        } catch (IOException e) {
            appendShell("无法启动 Shell: " + e.getMessage() + "\n");
        }
    }

    public void stopShell() {
        if (shellProcess != null && shellProcess.isAlive()) {
            shellProcess.destroyForcibly();
        }
        shellProcess = null;
        shellIn = null;
    }

    public void appendToolLog(String line) {
        Platform.runLater(() -> {
            String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            toolOutput.appendText("[" + ts + "] " + line + "\n");
            toolOutput.positionCaret(toolOutput.getLength());
        });
    }

    private VBox buildShellPane() {
        shellOutput.setEditable(false);
        shellOutput.setWrapText(true);
        shellOutput.getStyleClass().add("terminal-log");
        VBox.setVgrow(shellOutput, Priority.ALWAYS);

        shellInput.setPromptText("输入命令后回车…");
        shellInput.getStyleClass().add("terminal-input");
        shellInput.setOnAction(e -> sendCommand(shellInput.getText()));

        Button runButton = new Button("运行");
        runButton.getStyleClass().add("ghost-button");
        runButton.setOnAction(e -> sendCommand(shellInput.getText()));

        HBox inputRow = new HBox(8, shellInput, runButton);
        inputRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        inputRow.setPadding(new Insets(6, 8, 8, 8));
        HBox.setHgrow(shellInput, Priority.ALWAYS);

        VBox pane = new VBox(4, shellOutput, inputRow);
        pane.setPadding(new Insets(4, 0, 0, 0));
        VBox.setVgrow(shellOutput, Priority.ALWAYS);
        return pane;
    }

    private VBox buildOutputPane() {
        toolOutput.setEditable(false);
        toolOutput.setWrapText(true);
        toolOutput.getStyleClass().add("terminal-log");
        VBox pane = new VBox(toolOutput);
        pane.setPadding(new Insets(4, 0, 0, 0));
        VBox.setVgrow(toolOutput, Priority.ALWAYS);
        return pane;
    }

    private void sendCommand(String command) {
        if (command == null || command.isBlank() || shellIn == null) {
            return;
        }
        appendShell("$ " + command + "\n");
        shellInput.clear();
        try {
            shellIn.write((command + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            shellIn.flush();
        } catch (IOException e) {
            appendShell("发送失败: " + e.getMessage() + "\n");
        }
    }

    private void readShellStream(Process process) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                appendShell(line + "\n");
            }
        } catch (IOException ignored) {
            // process closed
        }
    }

    private void appendShell(String text) {
        Platform.runLater(() -> {
            shellOutput.appendText(text);
            shellOutput.positionCaret(shellOutput.getLength());
        });
    }
}

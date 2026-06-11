package com.aicode.app.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Hierarchical Workspaces sidebar matching Cursor-style layout. */
public final class AgentSidebarNav {
    private static final int VISIBLE_SESSION_LIMIT = 5;

    private final VBox container;
    private final ScrollPane scrollPane;
    private final Button addWorkspaceButton;

    private Consumer<WorkspaceContext> onSelectWorkspace;
    private BiConsumer<WorkspaceContext, ConversationContext> onSelectConversation;
    private Consumer<WorkspaceContext> onAddConversation;
    private Runnable onAddWorkspace;

    private List<WorkspaceContext> workspaces = List.of();
    private WorkspaceContext activeWorkspace;
    private ConversationContext activeConversation;

    public AgentSidebarNav(VBox container, ScrollPane scrollPane, Button addWorkspaceButton) {
        this.container = container;
        this.scrollPane = scrollPane;
        this.addWorkspaceButton = addWorkspaceButton;
        addWorkspaceButton.setOnAction(e -> {
            if (onAddWorkspace != null) {
                onAddWorkspace.run();
            }
        });
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
    }

    public void setOnAddWorkspace(Runnable onAddWorkspace) {
        this.onAddWorkspace = onAddWorkspace;
    }

    public void setOnSelectWorkspace(Consumer<WorkspaceContext> onSelectWorkspace) {
        this.onSelectWorkspace = onSelectWorkspace;
    }

    public void setOnSelectConversation(BiConsumer<WorkspaceContext, ConversationContext> onSelectConversation) {
        this.onSelectConversation = onSelectConversation;
    }

    public void setOnAddConversation(Consumer<WorkspaceContext> onAddConversation) {
        this.onAddConversation = onAddConversation;
    }

    public void render(
            List<WorkspaceContext> workspaces,
            WorkspaceContext activeWorkspace,
            ConversationContext activeConversation
    ) {
        this.workspaces = workspaces;
        this.activeWorkspace = activeWorkspace;
        this.activeConversation = activeConversation;
        container.getChildren().clear();
        for (WorkspaceContext workspace : workspaces) {
            container.getChildren().add(buildWorkspaceGroup(workspace));
        }
    }

    private VBox buildWorkspaceGroup(WorkspaceContext workspace) {
        VBox group = new VBox(2);
        group.getStyleClass().add("agent-ws-group");

        HBox header = new HBox(6);
        header.getStyleClass().add("agent-ws-header");
        header.setAlignment(Pos.CENTER_LEFT);

        Button chevron = new Button(workspace.expanded() ? "▾" : "▸");
        chevron.getStyleClass().add("agent-ws-chevron");
        chevron.setOnAction(e -> {
            workspace.setExpanded(!workspace.expanded());
            render(workspaces, activeWorkspace, activeConversation);
        });

        Label folder = new Label("📁");
        folder.getStyleClass().add("agent-ws-folder-icon");

        Label name = new Label(workspace.displayName());
        name.getStyleClass().add("agent-ws-name");
        if (workspace == activeWorkspace) {
            name.getStyleClass().add("agent-ws-name-active");
        }
        name.setOnMouseClicked(e -> {
            if (onSelectWorkspace != null) {
                onSelectWorkspace.accept(workspace);
            }
        });
        Tooltip.install(name, new Tooltip(workspace.path().toString()));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addSession = new Button("+");
        addSession.getStyleClass().add("agent-ws-add-session");
        addSession.setOnAction(e -> {
            if (onAddConversation != null) {
                onAddConversation.accept(workspace);
            }
        });

        header.getChildren().addAll(chevron, folder, name, spacer, addSession);
        group.getChildren().add(header);

        if (workspace.expanded()) {
            VBox sessions = buildSessionList(workspace);
            group.getChildren().add(sessions);
        }
        return group;
    }

    private VBox buildSessionList(WorkspaceContext workspace) {
        VBox list = new VBox(2);
        list.getStyleClass().add("agent-ws-sessions");

        List<ConversationContext> conversations = workspace.conversations();
        int limit = workspace.showAllSessions() ? conversations.size() : VISIBLE_SESSION_LIMIT;
        int shown = Math.min(limit, conversations.size());

        for (int i = 0; i < shown; i++) {
            list.getChildren().add(buildSessionRow(workspace, conversations.get(i)));
        }

        if (conversations.size() > VISIBLE_SESSION_LIMIT && !workspace.showAllSessions()) {
            Label seeMore = new Label("See more");
            seeMore.getStyleClass().add("agent-ws-see-more");
            seeMore.setOnMouseClicked(e -> {
                workspace.setShowAllSessions(true);
                render(workspaces, activeWorkspace, activeConversation);
            });
            list.getChildren().add(seeMore);
        }
        return list;
    }

    private HBox buildSessionRow(WorkspaceContext workspace, ConversationContext conversation) {
        HBox row = new HBox(8);
        row.getStyleClass().add("agent-ws-session-row");
        row.setAlignment(Pos.CENTER_LEFT);
        if (conversation == activeConversation) {
            row.getStyleClass().add("agent-ws-session-selected");
        }

        Label bullet = new Label("•");
        bullet.getStyleClass().add("agent-ws-session-bullet");

        String title = conversation.title();
        if (title.length() > 30) {
            title = title.substring(0, 30) + "…";
        }
        Label label = new Label(title);
        label.getStyleClass().add("agent-ws-session-title");

        row.getChildren().addAll(bullet, label);
        row.setOnMouseClicked(e -> {
            if (onSelectConversation != null) {
                onSelectConversation.accept(workspace, conversation);
            }
        });
        Tooltip.install(row, new Tooltip(workspace.path().toString()));
        return row;
    }
}

package com.aicode.app.ui;

import com.aicode.app.application.AgentApplication;
import com.aicode.app.config.WorkingDirectory;
import com.aicode.app.session.AgentSessionService;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A workspace directory with its own agent service and conversation list. */
public final class WorkspaceContext {
    private final Path path;
    private final List<ConversationContext> conversations = new ArrayList<>();
    private AgentApplication application;
    private AgentSessionService sessionService;
    private boolean expanded = true;
    private boolean showAllSessions;

    public WorkspaceContext(Path path) {
        this.path = path;
    }

    public boolean expanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public boolean showAllSessions() {
        return showAllSessions;
    }

    public void setShowAllSessions(boolean showAllSessions) {
        this.showAllSessions = showAllSessions;
    }

    public Path path() {
        return path;
    }

    public String displayName() {
        return WorkingDirectory.displayName(path);
    }

    public List<ConversationContext> conversations() {
        return Collections.unmodifiableList(conversations);
    }

    void addConversation(ConversationContext conversation) {
        conversations.add(conversation);
    }

    void removeConversation(ConversationContext conversation) {
        conversations.remove(conversation);
    }

    void clearConversations() {
        conversations.clear();
    }

    AgentApplication application() {
        return application;
    }

    void setApplication(AgentApplication application) {
        this.application = application;
    }

    AgentSessionService sessionService() {
        return sessionService;
    }

    void setSessionService(AgentSessionService sessionService) {
        this.sessionService = sessionService;
    }

    boolean isReady() {
        return sessionService != null;
    }
}

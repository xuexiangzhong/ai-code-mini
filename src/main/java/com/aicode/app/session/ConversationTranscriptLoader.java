package com.aicode.app.session;

import com.aicode.app.ui.ConversationTranscript;

import java.nio.file.Path;
import java.util.List;

/** Rebuild UI transcript from persisted message DTOs. */
public final class ConversationTranscriptLoader {
    private ConversationTranscriptLoader() {}

    public static void loadTurns(ConversationTranscript transcript, List<ChatTurnDto> turns) {
        transcript.clear();
        appendTurns(transcript, turns);
    }

    public static void prependTurns(ConversationTranscript transcript, List<ChatTurnDto> turns) {
        if (turns == null || turns.isEmpty()) {
            return;
        }
        for (int i = turns.size() - 1; i >= 0; i--) {
            prependTurn(transcript, turns.get(i));
        }
    }

    public static SessionPersistence.HistoryPage loadRecentPage(
            ConversationTranscript transcript,
            AgentSessionService service,
            Path workspace,
            String sessionId,
            ChatMode mode
    ) {
        SessionPersistence.HistoryPage page = service.loadTranscriptPage(
                workspace,
                sessionId,
                mode,
                SessionPersistence.DEFAULT_PAGE_SIZE,
                -1
        );
        loadTurns(transcript, page.turns());
        return page;
    }

    public static SessionPersistence.HistoryPage loadOlderPage(
            ConversationTranscript transcript,
            AgentSessionService service,
            Path workspace,
            String sessionId,
            ChatMode mode,
            int beforeTurnIndex
    ) {
        SessionPersistence.HistoryPage page = service.loadTranscriptPage(
                workspace,
                sessionId,
                mode,
                SessionPersistence.DEFAULT_PAGE_SIZE,
                beforeTurnIndex
        );
        prependTurns(transcript, page.turns());
        return page;
    }

    /** Rebuild UI transcript from backend session history (unless still generating). */
    public static void syncFromBackend(
            ConversationTranscript transcript,
            AgentSessionService service,
            String sessionId,
            ChatMode mode,
            boolean generating
    ) {
        if (service == null || generating) {
            return;
        }
        try {
            loadTurns(transcript, service.getTranscriptTurns(sessionId, mode));
        } catch (IllegalArgumentException ignored) {
            // session not registered in this service instance
        }
    }

    /** @deprecated use {@link #loadTurns(ConversationTranscript, List)} */
    @Deprecated
    public static void loadAgentHistory(ConversationTranscript transcript, List<ChatMessageDto> messages) {
        transcript.clear();
        for (ChatMessageDto message : messages) {
            switch (message.role()) {
                case "user" -> transcript.startTurn(message.content());
                case "assistant" -> transcript.appendAssistant(message.content());
                default -> transcript.addStandaloneNotice(message.content());
            }
        }
    }

    private static void appendTurns(ConversationTranscript transcript, List<ChatTurnDto> turns) {
        for (ChatTurnDto turn : turns) {
            appendTurn(transcript, turn);
        }
    }

    private static void appendTurn(ConversationTranscript transcript, ChatTurnDto turn) {
        if (turn.standalone()) {
            transcript.addStandaloneNotice(turn.assistantText(), turn.createdAt());
            return;
        }
        transcript.startTurn(turn.userText(), turn.createdAt(), turn.userImagePaths());
        for (String activity : turn.activities()) {
            transcript.addActivity(activity);
        }
        if (turn.assistantText() != null && !turn.assistantText().isBlank()) {
            transcript.appendAssistant(turn.assistantText());
        }
    }

    private static void prependTurn(ConversationTranscript transcript, ChatTurnDto turn) {
        if (turn.standalone()) {
            transcript.prependStandaloneNotice(turn.assistantText(), turn.createdAt());
            return;
        }
        transcript.prependTurn(
                turn.userText(),
                turn.activities(),
                turn.assistantText(),
                turn.createdAt(),
                turn.userImagePaths()
        );
    }
}

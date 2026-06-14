package com.aicode.app.session;

import com.aicode.app.config.AicodePaths;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Persists chat sessions under {@code .aicode/sessions/{workspaceKey}/{sessionId}/}:
 * {@code summary.json} (metadata) and {@code history.json} (messages + UI turns).
 */
public final class SessionPersistence {
    public static final int DEFAULT_PAGE_SIZE = 15;

    public record StoredMessage(
            String role,
            String content,
            String displayContent,
            List<PersistedBlockRef> blocks
    ) {
        public StoredMessage(String role, String content) {
            this(role, content, null, List.of());
        }

        public record PersistedBlockRef(
                String type,
                String text,
                String id,
                String name,
                java.util.Map<String, Object> input,
                String toolUseId,
                String content,
                Boolean isError
        ) {}
    }

    public record StoredSessionSummary(
            String sessionId,
            String title,
            String workspace,
            String updatedAt,
            String compressedSummary,
            String agentCompressedSummary,
            String chatCompressedSummary,
            int totalAgentTurns,
            int totalChatTurns
    ) {
        /** Legacy field fallback for agent-mode summary. */
        public String effectiveAgentCompressedSummary() {
            if (agentCompressedSummary != null && !agentCompressedSummary.isBlank()) {
                return agentCompressedSummary;
            }
            return compressedSummary;
        }

        public String effectiveChatCompressedSummary() {
            return chatCompressedSummary;
        }
    }

    public record StoredSessionHistory(
            List<StoredMessage> agentMessages,
            List<StoredMessage> chatMessages,
            List<ChatTurnDto> agentTurns,
            List<ChatTurnDto> chatTurns
    ) {
        public StoredSessionHistory {
            agentMessages = agentMessages != null ? agentMessages : List.of();
            chatMessages = chatMessages != null ? chatMessages : List.of();
            agentTurns = agentTurns != null ? agentTurns : List.of();
            chatTurns = chatTurns != null ? chatTurns : List.of();
        }
    }

    /** One page of UI turns plus pagination metadata. */
    public record HistoryPage(
            List<ChatTurnDto> turns,
            int totalTurns,
            int startIndex,
            boolean hasOlder
    ) {}

    /** Combined view for session restore (built from summary + history). */
    public record StoredSession(
            String sessionId,
            String title,
            String workspace,
            List<StoredMessage> agentMessages,
            List<StoredMessage> chatMessages,
            String updatedAt
    ) {
        public StoredSession withUpdated(List<StoredMessage> agent, List<StoredMessage> chat, String title) {
            return new StoredSession(
                    sessionId,
                    title,
                    workspace,
                    List.copyOf(agent),
                    List.copyOf(chat),
                    Instant.now().toString()
            );
        }
    }

    private static final String SUMMARY_FILE = "summary.json";
    private static final String HISTORY_FILE = "history.json";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path rootDir;

    public SessionPersistence() {
        this(defaultRootDir());
    }

    SessionPersistence(Path rootDir) {
        this.rootDir = rootDir;
    }

    private static Path defaultRootDir() {
        Path dir = AicodePaths.sessionsDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            // best-effort
        }
        return dir;
    }

    /** Creates {@code summary.json} and empty {@code history.json} for a new session. */
    public void createEmpty(Path workspace, String sessionId, String title) throws IOException {
        String normalizedWorkspace = normalizeWorkspaceString(workspace.toString());
        Path dir = sessionDir(normalizedWorkspace, sessionId);
        Files.createDirectories(dir);
        StoredSessionSummary summary = new StoredSessionSummary(
                sessionId,
                title != null && !title.isBlank() ? title.strip() : "对话",
                normalizedWorkspace,
                Instant.now().toString(),
                null,
                null,
                null,
                0,
                0
        );
        MAPPER.writeValue(dir.resolve(SUMMARY_FILE).toFile(), summary);
        MAPPER.writeValue(dir.resolve(HISTORY_FILE).toFile(), emptyHistory());
    }

    /** Updates {@code summary.json} and {@code history.json} for an existing session directory. */
    public void saveSplit(StoredSessionSummary summary, StoredSessionHistory history) throws IOException {
        String workspace = normalizeWorkspaceString(summary.workspace());
        Path dir = sessionDir(workspace, summary.sessionId());
        if (!Files.isDirectory(dir)) {
            throw new IOException("Session directory does not exist: " + dir);
        }
        StoredSessionSummary normalizedSummary = new StoredSessionSummary(
                summary.sessionId(),
                summary.title(),
                workspace,
                summary.updatedAt() != null ? summary.updatedAt() : Instant.now().toString(),
                summary.compressedSummary(),
                summary.agentCompressedSummary(),
                summary.chatCompressedSummary(),
                history.agentTurns().size(),
                history.chatTurns().size()
        );
        MAPPER.writeValue(dir.resolve(SUMMARY_FILE).toFile(), normalizedSummary);
        MAPPER.writeValue(dir.resolve(HISTORY_FILE).toFile(), history);
    }

    public List<StoredSessionSummary> loadSummaries(Path workspace) throws IOException {
        Path wsDir = workspaceDir(normalizeWorkspaceString(workspace.toString()));
        if (!Files.isDirectory(wsDir)) {
            return List.of();
        }
        removeLegacyFlatFiles(wsDir);
        List<StoredSessionSummary> summaries = new ArrayList<>();
        try (var stream = Files.list(wsDir)) {
            for (Path entry : stream.filter(Files::isDirectory).toList()) {
                Path summaryFile = entry.resolve(SUMMARY_FILE);
                if (!Files.isRegularFile(summaryFile)) {
                    deleteDirectoryQuietly(entry);
                    continue;
                }
                try {
                    StoredSessionSummary summary =
                            normalizeSummary(MAPPER.readValue(summaryFile.toFile(), StoredSessionSummary.class));
                    summaries.add(summary);
                } catch (IOException ignored) {
                    deleteDirectoryQuietly(entry);
                }
            }
        }
        summaries.sort(Comparator
                .comparing(SessionPersistence::summaryHasTurns).reversed()
                .thenComparing(StoredSessionSummary::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return summaries;
    }

    public StoredSessionHistory loadHistory(Path workspace, String sessionId) throws IOException {
        Path historyFile = sessionDir(normalizeWorkspaceString(workspace.toString()), sessionId).resolve(HISTORY_FILE);
        return readHistoryFile(historyFile);
    }

    public StoredSessionSummary loadSummary(Path workspace, String sessionId) throws IOException {
        Path summaryFile = sessionDir(normalizeWorkspaceString(workspace.toString()), sessionId).resolve(SUMMARY_FILE);
        if (!Files.isRegularFile(summaryFile)) {
            throw new IOException("Summary file does not exist: " + summaryFile);
        }
        return normalizeSummary(MAPPER.readValue(summaryFile.toFile(), StoredSessionSummary.class));
    }

    public HistoryPage loadTurnPage(
            Path workspace,
            String sessionId,
            ChatMode mode,
            int limit,
            int beforeTurnIndex
    ) throws IOException {
        StoredSessionHistory history = loadHistory(workspace, sessionId);
        List<ChatTurnDto> allTurns = turnsForMode(history, mode);
        int total = allTurns.size();
        int end = beforeTurnIndex < 0 || beforeTurnIndex > total ? total : beforeTurnIndex;
        int start = Math.max(0, end - Math.max(1, limit));
        return new HistoryPage(allTurns.subList(start, end), total, start, start > 0);
    }

    public List<StoredSession> loadAll(Path workspace) throws IOException {
        List<StoredSession> sessions = new ArrayList<>();
        for (StoredSessionSummary summary : loadSummaries(workspace)) {
            StoredSessionHistory history = loadHistory(workspace, summary.sessionId());
            sessions.add(toStoredSession(summary, history));
        }
        return sessions;
    }

    public boolean exists(Path workspace, String sessionId) {
        String normalized = normalizeWorkspaceString(workspace.toString());
        return Files.isRegularFile(sessionDir(normalized, sessionId).resolve(SUMMARY_FILE));
    }

    public void delete(Path workspace, String sessionId) throws IOException {
        Path dir = sessionDir(normalizeWorkspaceString(workspace.toString()), sessionId);
        if (Files.isDirectory(dir)) {
            deleteDirectory(dir);
        }
    }

    static StoredSession toStoredSession(StoredSessionSummary summary, StoredSessionHistory history) {
        return new StoredSession(
                summary.sessionId(),
                summary.title(),
                summary.workspace(),
                history.agentMessages(),
                history.chatMessages(),
                summary.updatedAt()
        );
    }

    static String normalizeWorkspaceString(String workspace) {
        if (workspace == null || workspace.isBlank()) {
            return workspace;
        }
        return Path.of(workspace).toAbsolutePath().normalize().toString();
    }

    static String workspaceKey(String workspace) {
        String normalized = normalizeWorkspaceString(workspace);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(normalized.hashCode());
        }
    }

    public static boolean hasMessages(StoredSession session) {
        return !isEmpty(session.agentMessages()) || !isEmpty(session.chatMessages());
    }

    private static boolean summaryHasTurns(StoredSessionSummary summary) {
        return summary.totalAgentTurns() > 0 || summary.totalChatTurns() > 0;
    }

    private static StoredSessionHistory emptyHistory() {
        return new StoredSessionHistory(List.of(), List.of(), List.of(), List.of());
    }

    private static List<ChatTurnDto> turnsForMode(StoredSessionHistory history, ChatMode mode) {
        return mode == ChatMode.CHAT ? history.chatTurns() : history.agentTurns();
    }

    private StoredSessionSummary normalizeSummary(StoredSessionSummary summary) {
        return new StoredSessionSummary(
                summary.sessionId(),
                summary.title(),
                normalizeWorkspaceString(summary.workspace()),
                summary.updatedAt(),
                summary.compressedSummary(),
                summary.agentCompressedSummary(),
                summary.chatCompressedSummary(),
                summary.totalAgentTurns(),
                summary.totalChatTurns()
        );
    }

    private static StoredSessionHistory readHistoryFile(Path historyFile) throws IOException {
        if (!Files.isRegularFile(historyFile)) {
            return new StoredSessionHistory(List.of(), List.of(), List.of(), List.of());
        }
        StoredSessionHistory loaded = MAPPER.readValue(historyFile.toFile(), StoredSessionHistory.class);
        return new StoredSessionHistory(
                loaded.agentMessages(),
                loaded.chatMessages(),
                loaded.agentTurns(),
                loaded.chatTurns()
        );
    }

    private static boolean isEmpty(List<?> messages) {
        return messages == null || messages.isEmpty();
    }

    private Path workspaceDir(String workspace) {
        return rootDir.resolve(workspaceKey(workspace));
    }

    private Path sessionDir(String workspace, String sessionId) {
        return workspaceDir(workspace).resolve(sessionId);
    }

    /** Remove obsolete flat {@code {sessionId}.json} files from the pre-split layout. */
    private void removeLegacyFlatFiles(Path wsDir) throws IOException {
        try (var stream = Files.list(wsDir)) {
            for (Path file : stream.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".json")).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static void deleteDirectoryQuietly(Path dir) {
        try {
            deleteDirectory(dir);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

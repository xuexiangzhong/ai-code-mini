package com.aicode.app.approval;

import com.aicode.app.session.FileEditProposal;
import com.aicode.app.session.FileEditRevertor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Blocks the agent after a file write until the user keeps or reverts the change. */
public final class FileEditGate {
    private static final long DEFAULT_TIMEOUT_MINUTES = 30;

    private record PendingEdit(FileEditProposal proposal, CompletableFuture<Boolean> future) {}

    private final ConcurrentHashMap<String, PendingEdit> pending = new ConcurrentHashMap<>();
    /** Paths the user already accepted during this agent run — skip re-prompting. */
    private final Set<String> keptPaths = ConcurrentHashMap.newKeySet();

    public CompletableFuture<Boolean> awaitReview(FileEditProposal proposal) {
        String pathKey = normalize(proposal.filePath());
        if (keptPaths.contains(pathKey)) {
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(proposal.id(), new PendingEdit(proposal, future));
        future.orTimeout(DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .exceptionally(ex -> {
                    pending.remove(proposal.id());
                    return false;
                });
        return future;
    }

    /** Whether this write still needs a user review card and agent blocking. */
    public boolean needsReview(Path filePath) {
        return !keptPaths.contains(normalize(filePath));
    }

    public void resolve(String editId, boolean kept) {
        PendingEdit pendingEdit = pending.remove(editId);
        if (pendingEdit == null) {
            return;
        }
        String pathKey = normalize(pendingEdit.proposal().filePath());
        if (!kept) {
            keptPaths.remove(pathKey);
            try {
                FileEditRevertor.revert(pendingEdit.proposal());
            } catch (IOException ignored) {
                // Still unblock the agent with a rejection result.
            }
        } else {
            keptPaths.add(pathKey);
        }
        pendingEdit.future().complete(kept);
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    /** Unblocks an in-flight agent run and reverts pending file edits (same as rejecting each). */
    public void cancelAll() {
        for (String editId : new java.util.ArrayList<>(pending.keySet())) {
            resolve(editId, false);
        }
    }

    private static String normalize(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }
}

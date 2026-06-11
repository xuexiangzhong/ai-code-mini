package com.aicode.app.approval;

import com.aicode.app.event.AgentEvent;
import com.aicode.app.event.AgentEventListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public final class ApprovalGate {
    private static final long DEFAULT_TIMEOUT_MINUTES = 5;

    private final ConcurrentHashMap<String, PendingApproval> pending = new ConcurrentHashMap<>();
    private final AgentEventListener listener;
    private final BiFunction<String, Map<String, Object>, Boolean> cliResolver;

    public ApprovalGate(AgentEventListener listener) {
        this(listener, null);
    }

    public ApprovalGate(AgentEventListener listener, BiFunction<String, Map<String, Object>, Boolean> cliResolver) {
        this.listener = listener != null ? listener : AgentEventListener.NOOP;
        this.cliResolver = cliResolver;
    }

    public CompletableFuture<Boolean> requestApproval(
            String toolName,
            Map<String, Object> input,
            String reason
    ) {
        String approvalId = UUID.randomUUID().toString();
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        pending.put(approvalId, new PendingApproval(approvalId, toolName, input, reason, future));
        listener.onEvent(new AgentEvent.ApprovalRequired(approvalId, toolName, input, reason));

        if (cliResolver != null) {
            CompletableFuture.runAsync(() -> {
                boolean approved = cliResolver.apply(toolName, input);
                resolve(approvalId, approved);
            });
        }

        future.orTimeout(DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .exceptionally(ex -> {
                    pending.remove(approvalId);
                    return false;
                });
        return future;
    }

    public void resolve(String approvalId, boolean approved) {
        PendingApproval pendingApproval = pending.remove(approvalId);
        if (pendingApproval != null) {
            listener.onEvent(new AgentEvent.ApprovalResolved(approvalId, approved));
            pendingApproval.future().complete(approved);
        }
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public void cancelAll() {
        for (PendingApproval pendingApproval : pending.values()) {
            pendingApproval.future().complete(false);
        }
        pending.clear();
    }
}

package com.aicode.app.approval;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public record PendingApproval(
        String approvalId,
        String toolName,
        Map<String, Object> input,
        String reason,
        CompletableFuture<Boolean> future
) {}

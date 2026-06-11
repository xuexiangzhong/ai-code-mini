package com.aicode.app.approval;

import com.aicode.app.event.AgentEvent;
import com.aicode.app.event.AgentEventListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ApprovalGateTest {

    @Test
    void resolveApprovalCompletesFuture() {
        List<AgentEvent> events = new ArrayList<>();
        ApprovalGate gate = new ApprovalGate(events::add);

        var future = gate.requestApproval("bash", Map.of("command", "rm -rf tmp"), "danger");
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.ApprovalRequired));

        AgentEvent.ApprovalRequired required = (AgentEvent.ApprovalRequired) events.stream()
                .filter(e -> e instanceof AgentEvent.ApprovalRequired)
                .findFirst()
                .orElseThrow();
        gate.resolve(required.approvalId(), true);

        assertTrue(future.join());
        assertTrue(events.stream().anyMatch(e -> e instanceof AgentEvent.ApprovalResolved));
    }

    @Test
    void rejectApprovalReturnsFalse() {
        List<AgentEvent> events = new ArrayList<>();
        ApprovalGate gate = new ApprovalGate(events::add);
        var future = gate.requestApproval("bash", Map.of("command", "sudo rm"), "danger");
        AgentEvent.ApprovalRequired required = (AgentEvent.ApprovalRequired) events.getFirst();
        gate.resolve(required.approvalId(), false);
        assertFalse(future.join());
    }
}

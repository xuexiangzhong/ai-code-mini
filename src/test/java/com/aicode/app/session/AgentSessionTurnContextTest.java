package com.aicode.app.session;

import com.aicode.agent.TurnContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentSessionTurnContextTest {
    @Test
    void pendingTurnContextConsumedOnce() {
        AgentSession session = new AgentSession(Path.of("/tmp/ws"), null);
        TurnContext ctx = TurnContext.of(Path.of("/tmp/ws"), null);
        session.setPendingTurnContext(ctx);
        assertSame(ctx, session.consumePendingTurnContext());
        assertNull(session.consumePendingTurnContext());
    }
}

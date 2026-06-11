package com.aicode.agent;

import com.aicode.agent.llm.OutputTokenLimits;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutputTokenLimitsTest {
    @Test
    void scaleOutputLimitByRetryAttempt() {
        OutputTokenLimits limits = new OutputTokenLimits(8192, 32768, 2);
        assertEquals(8192, limits.limitForAttempt(0));
        assertEquals(16384, limits.limitForAttempt(1));
        assertEquals(32768, limits.limitForAttempt(2));
        assertEquals(32768, limits.limitForAttempt(3));
    }

    @Test
    void allowRetryWhileBelowCap() {
        OutputTokenLimits limits = new OutputTokenLimits(8192, 32768, 2);
        assertTrue(limits.canRetry(0));
        assertTrue(limits.canRetry(1));
        assertFalse(limits.canRetry(2));
    }
}

package com.aicode.app.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TurnTimeFormatTest {
    @Test
    void formatsIsoInstant() {
        String today = java.time.Instant.now().toString();
        assertFalse(TurnTimeFormat.display(today).isBlank());
    }

    @Test
    void blankForNull() {
        assertEquals("", TurnTimeFormat.display(null));
        assertEquals("", TurnTimeFormat.display(""));
    }
}

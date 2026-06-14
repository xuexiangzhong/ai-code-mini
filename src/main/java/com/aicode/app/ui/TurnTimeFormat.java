package com.aicode.app.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formats ISO-8601 turn timestamps for chat UI. */
public final class TurnTimeFormat {
    private static final DateTimeFormatter TIME_TODAY = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME_OTHER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private TurnTimeFormat() {}

    public static String display(String isoInstant) {
        if (isoInstant == null || isoInstant.isBlank()) {
            return "";
        }
        try {
            Instant instant = Instant.parse(isoInstant);
            LocalDateTime local = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            if (local.toLocalDate().equals(LocalDate.now())) {
                return local.format(TIME_TODAY);
            }
            return local.format(TIME_OTHER);
        } catch (Exception ignored) {
            return "";
        }
    }
}

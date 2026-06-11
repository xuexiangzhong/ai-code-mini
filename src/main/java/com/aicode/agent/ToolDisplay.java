package com.aicode.agent;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tool call visualization: spinner animation, parameter display, timing.
 */
public final class ToolDisplay {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private ToolDisplay() {}

    public static class Spinner {
        private String message;
        private int frameIndex;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private Thread thread;

        public Spinner(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }

        public String currentFrame() {
            return FRAMES[frameIndex % FRAMES.length];
        }

        public void start() {
            if (running.getAndSet(true)) {
                return;
            }
            thread = Thread.ofVirtual().name("spinner").start(() -> {
                while (running.get()) {
                    String frame = FRAMES[frameIndex % FRAMES.length];
                    System.err.print("\r" + Markdown.CYAN + frame + Markdown.RESET + " " + message);
                    System.err.flush();
                    frameIndex++;
                    try {
                        Thread.sleep(80);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        public void update(String message) {
            this.message = message;
        }

        public void stop() {
            running.set(false);
            if (thread != null) {
                try {
                    thread.join(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                thread = null;
            }
            System.err.print("\r\033[K");
            System.err.flush();
        }

        public void succeed(String message) {
            stop();
            System.err.println(Markdown.GREEN + "✔" + Markdown.RESET + " " + (message != null ? message : this.message));
        }

        public void fail(String message) {
            stop();
            System.err.println(Markdown.YELLOW + "✖" + Markdown.RESET + " " + (message != null ? message : this.message));
        }

        public boolean isRunning() {
            return running.get();
        }
    }

    public static String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 3) + "...";
    }

    public static String formatParams(Map<String, Object> input, int maxLen) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            String val = e.getValue() instanceof String s
                    ? "\"" + truncate(s, 40) + "\""
                    : safeJson(e.getValue());
            sb.append(e.getKey()).append(": ").append(val);
        }
        String joined = sb.toString();
        return joined.length() <= maxLen ? joined : joined.substring(0, maxLen - 3) + "...";
    }

    private static String safeJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    public static String formatToolCall(String name, Map<String, Object> input) {
        return Markdown.MAGENTA + "🔧 " + name + Markdown.RESET
                + Markdown.DIM + "(" + formatParams(input, 80) + ")" + Markdown.RESET;
    }

    public static String formatDuration(double ms) {
        if (ms < 1000) {
            return Math.round(ms) + "ms";
        }
        if (ms < 60000) {
            return String.format("%.1fs", ms / 1000);
        }
        return String.format("%.1fm", ms / 60000);
    }

    public static String formatToolResult(String result, int maxLines, int maxLineLen) {
        String[] lines = result.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(lines.length, maxLines);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append("\n");
            }
            String line = lines[i];
            sb.append(line.length() <= maxLineLen ? line : line.substring(0, maxLineLen - 3) + "...");
        }
        if (lines.length > maxLines) {
            sb.append("\n").append(Markdown.GRAY)
                    .append("... (").append(lines.length - maxLines).append(" more lines)")
                    .append(Markdown.RESET);
        }
        return sb.toString();
    }

    public static String formatToolCycle(String name, Map<String, Object> input, String result, double durationMs) {
        String header = formatToolCall(name, input);
        String timeStr = Markdown.DIM + "[" + formatDuration(durationMs) + "]" + Markdown.RESET;
        String body = formatToolResult(result, 5, 120);
        return header + " " + timeStr + "\n" + body;
    }
}

package com.aicode.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Terminal line reader with CJK-aware backspace (mirrors Python {@code repl._read_line}
 * and TypeScript {@code readLine}).
 */
public final class TermReader {
    private TermReader() {}

    public static int charWidth(int codePoint) {
        if (codePoint > 0xFFFF) {
            return 2;
        }
        if ((codePoint >= 0x2E80 && codePoint <= 0x9FFF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE4F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)) {
            return 2;
        }
        return 1;
    }

    public static int charWidth(String ch) {
        if (ch == null || ch.isEmpty()) {
            return 0;
        }
        return charWidth(ch.codePointAt(0));
    }

    public static boolean isTTY() {
        return System.console() != null;
    }

    public static String readLine(String prompt) throws IOException {
        if (!isTTY()) {
            return readLineSimple(prompt);
        }
        return readLineRaw(prompt);
    }

    private static String readLineSimple(String prompt) throws IOException {
        System.out.print(prompt);
        System.out.flush();
        return new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
    }

    private static String readLineRaw(String prompt) throws IOException {
        String savedStty = sttyGet();
        try {
            sttySet("-icanon", "min", "1", "-echo");
            System.out.print(prompt);
            System.out.flush();

            List<String> chars = new ArrayList<>();
            java.io.InputStream in = System.in;

            while (true) {
                int b = in.read();
                if (b < 0) {
                    System.out.println();
                    return null;
                }

                if (b == 0x0A || b == 0x0D) {
                    System.out.println();
                    return String.join("", chars);
                }

                if (b == 0x7F || b == 0x08) {
                    if (!chars.isEmpty()) {
                        String removed = chars.removeLast();
                        System.out.print("\b \b".repeat(charWidth(removed)));
                        System.out.flush();
                    }
                    continue;
                }

                if (b == 0x03) {
                    System.out.println();
                    return null;
                }

                if (b == 0x04) {
                    if (chars.isEmpty()) {
                        System.out.println();
                        return null;
                    }
                    continue;
                }

                if (b == 0x15) {
                    while (!chars.isEmpty()) {
                        String removed = chars.removeLast();
                        System.out.print("\b \b".repeat(charWidth(removed)));
                    }
                    System.out.flush();
                    continue;
                }

                if (b == 0x1B) {
                    if (in.available() > 0) {
                        in.read();
                    }
                    if (in.available() > 0) {
                        in.read();
                    }
                    continue;
                }

                if (b < 0x20) {
                    continue;
                }

                String ch = decodeUtf8Byte(in, b);
                if (ch != null && !"\uFFFD".equals(ch)) {
                    chars.add(ch);
                    System.out.print(ch);
                    System.out.flush();
                }
            }
        } finally {
            if (savedStty != null && !savedStty.isBlank()) {
                sttyRestore(savedStty);
            }
        }
    }

    private static String decodeUtf8Byte(java.io.InputStream in, int firstByte) throws IOException {
        if (firstByte < 0x80) {
            return String.valueOf((char) firstByte);
        }
        if (firstByte < 0xC0) {
            return null;
        }
        int extra = firstByte < 0xE0 ? 1 : firstByte < 0xF0 ? 2 : 3;
        byte[] bytes = new byte[1 + extra];
        bytes[0] = (byte) firstByte;
        readExact(in, bytes, 1, extra);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void readExact(java.io.InputStream in, byte[] buf, int offset, int len) throws IOException {
        int read = 0;
        while (read < len) {
            int n = in.read(buf, offset + read, len - read);
            if (n < 0) {
                break;
            }
            read += n;
        }
    }

    private static String sttyGet() {
        try {
            Process p = new ProcessBuilder("stty", "-g")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor(2, TimeUnit.SECONDS);
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static void sttySet(String... args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("stty");
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).inheritIO().start();
        try {
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private static void sttyRestore(String settings) {
        try {
            Process p = new ProcessBuilder("sh", "-c", "stty " + settings).inheritIO().start();
            p.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // best effort
        }
    }
}

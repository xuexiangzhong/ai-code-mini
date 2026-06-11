package com.aicode.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Terminal markdown renderer using ANSI escape codes.
 */
public final class Markdown {
    public static final String RESET = "\033[0m";
    public static final String BOLD = "\033[1m";
    public static final String DIM = "\033[2m";
    public static final String ITALIC = "\033[3m";
    public static final String CYAN = "\033[36m";
    public static final String GREEN = "\033[32m";
    public static final String YELLOW = "\033[33m";
    public static final String MAGENTA = "\033[35m";
    public static final String GRAY = "\033[90m";
    public static final String BG_GRAY = "\033[48;5;236m";
    public static final String WHITE = "\033[97m";

    private Markdown() {}

    public static String renderInline(String text) {
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", BOLD + "$1" + RESET);
        text = text.replaceAll("__(.+?)__", BOLD + "$1" + RESET);
        text = text.replaceAll("`([^`]+)`", CYAN + "$1" + RESET);
        text = text.replaceAll("(?<!\\w)\\*([^*]+)\\*(?!\\w)", ITALIC + "$1" + RESET);
        text = text.replaceAll("(?<!\\w)_([^_]+)_(?!\\w)", ITALIC + "$1" + RESET);
        return text;
    }

    public static String renderCodeBlock(String code, String language) {
        String header = language.isEmpty()
                ? GRAY + "┌" + "─".repeat(44) + "┐" + RESET + "\n"
                : GRAY + "┌─ " + language + " " + "─".repeat(Math.max(0, 40 - language.length())) + "┐" + RESET + "\n";
        List<String> lines = new ArrayList<>();
        for (String line : code.split("\n", -1)) {
            lines.add(GRAY + "│" + RESET + " " + BG_GRAY + WHITE + line + RESET);
        }
        return header + String.join("\n", lines) + "\n" + GRAY + "└" + "─".repeat(44) + "┘" + RESET;
    }

    public static String renderHeading(String text, int level) {
        String prefix = switch (level) {
            case 1 -> BOLD + MAGENTA;
            case 2 -> BOLD + GREEN;
            default -> BOLD + YELLOW;
        };
        return "\n" + prefix + "#".repeat(level) + " " + text + RESET + "\n";
    }

    public static String renderListItem(String text, int indent) {
        return " ".repeat(indent) + GREEN + "•" + RESET + " " + renderInline(text);
    }

    public static String renderHorizontalRule() {
        return GRAY + "─".repeat(48) + RESET;
    }

    public static String renderMarkdown(String markdown) {
        String[] lines = markdown.split("\n", -1);
        List<String> output = new ArrayList<>();
        boolean inCodeBlock = false;
        String codeLanguage = "";
        List<String> codeBuffer = new ArrayList<>();

        for (String line : lines) {
            if (line.stripLeading().startsWith("```")) {
                if (inCodeBlock) {
                    output.add(renderCodeBlock(String.join("\n", codeBuffer), codeLanguage));
                    codeBuffer.clear();
                    inCodeBlock = false;
                    codeLanguage = "";
                } else {
                    inCodeBlock = true;
                    codeLanguage = line.stripLeading().substring(3).strip();
                }
                continue;
            }

            if (inCodeBlock) {
                codeBuffer.add(line);
                continue;
            }

            String stripped = line.strip();
            if (stripped.matches("^---+$") || stripped.matches("^\\*\\*\\*+$")) {
                output.add(renderHorizontalRule());
                continue;
            }

            Matcher headingMatch = Pattern.compile("^(#{1,3})\\s+(.+)").matcher(line);
            if (headingMatch.find()) {
                output.add(renderHeading(headingMatch.group(2), headingMatch.group(1).length()));
                continue;
            }

            Matcher listMatch = Pattern.compile("^(\\s*)[*-]\\s+(.+)").matcher(line);
            if (listMatch.find()) {
                output.add(renderListItem(listMatch.group(2), listMatch.group(1).length()));
                continue;
            }

            Matcher orderedMatch = Pattern.compile("^(\\s*)\\d+\\.\\s+(.+)").matcher(line);
            if (orderedMatch.find()) {
                output.add(renderListItem(orderedMatch.group(2), orderedMatch.group(1).length()));
                continue;
            }

            if (stripped.isEmpty()) {
                output.add("");
                continue;
            }

            output.add(renderInline(line));
        }

        if (inCodeBlock && !codeBuffer.isEmpty()) {
            output.add(renderCodeBlock(String.join("\n", codeBuffer), codeLanguage));
        }

        return String.join("\n", output);
    }

    public static String stripAnsi(String text) {
        return text.replaceAll("\033\\[[0-9;]*[a-zA-Z]", "");
    }
}

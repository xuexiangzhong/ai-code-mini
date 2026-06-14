package com.aicode.agent.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Unified diff text for file edits (used by write_file and search_replace). */
public final class FileDiff {
    private FileDiff() {}

    public static String generate(String oldContent, String newContent, String filePath) {
        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);
        StringBuilder result = new StringBuilder();
        result.append("--- a/").append(filePath).append("\n");
        result.append("+++ b/").append(filePath).append("\n");

        int maxLen = Math.max(oldLines.length, newLines.length);
        boolean hasChanges = false;
        List<String> hunks = new ArrayList<>();

        for (int i = 0; i < maxLen; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : null;
            String newLine = i < newLines.length ? newLines[i] : null;
            if (Objects.equals(oldLine, newLine)) {
                hunks.add(" " + oldLine);
            } else {
                hasChanges = true;
                if (oldLine != null) {
                    hunks.add("-" + oldLine);
                }
                if (newLine != null) {
                    hunks.add("+" + newLine);
                }
            }
        }

        if (!hasChanges) {
            return "(no changes)";
        }

        result.append(String.format("@@ -1,%d +1,%d @@\n", oldLines.length, newLines.length));
        for (String hunk : hunks) {
            result.append(hunk).append("\n");
        }
        return result.toString().stripTrailing();
    }
}

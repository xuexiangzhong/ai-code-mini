package com.aicode.app.approval;

import java.io.Console;
import java.util.Map;

public final class CliApprovalHandler {
    private CliApprovalHandler() {}

    public static boolean prompt(String toolName, Map<String, Object> input) {
        System.err.print("Approve " + toolName + " " + input + "? [y/N]: ");
        System.err.flush();
        Console console = System.console();
        String answer;
        if (console != null) {
            answer = console.readLine();
        } else {
            try {
                answer = new String(System.in.readNBytes(System.in.available() > 0 ? System.in.available() : 1));
            } catch (Exception e) {
                return false;
            }
        }
        return answer != null && answer.trim().equalsIgnoreCase("y");
    }
}

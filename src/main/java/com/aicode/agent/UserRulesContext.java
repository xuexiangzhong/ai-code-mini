package com.aicode.agent;

import java.nio.file.Path;

/** Loads global user rules from {@code ~/.aicode/rules} and {@code ~/.cursor/rules}. */
public final class UserRulesContext {
    private UserRulesContext() {}

    public static Path userRulesDir() {
        return RuleContext.userAicodeRulesDir();
    }

    public static String loadForPrompt(Path workspace) {
        return RuleContext.formatAlwaysApply(
                RuleContext.forScope(RuleContext.discover(workspace), "user")
        );
    }
}

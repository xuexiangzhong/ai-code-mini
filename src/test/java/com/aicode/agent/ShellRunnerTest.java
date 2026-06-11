package com.aicode.agent;

import com.aicode.agent.tools.ShellRunner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShellRunnerTest {
    @Test
    void resolveProducesNonEmptyCommand() {
        ShellRunner.ShellInvocation invocation = ShellRunner.resolve("echo test");
        assertFalse(invocation.command().isEmpty());
        assertNotNull(invocation.kind());
    }

    @Test
    void activeShellDescriptionIsNonBlank() {
        assertFalse(ShellRunner.activeShellDescription().isBlank());
    }

    @Test
    void unixPlatformsPreferBashOrSh() {
        if (!ShellRunner.isWindows()) {
            assertTrue(
                    ShellRunner.activeShellKind() == ShellRunner.ShellKind.BASH
                            || ShellRunner.activeShellKind() == ShellRunner.ShellKind.SH
            );
        }
    }

    @Test
    void windowsFallsBackToKnownShell() {
        if (ShellRunner.isWindows()) {
            assertTrue(
                    ShellRunner.activeShellKind() == ShellRunner.ShellKind.BASH
                            || ShellRunner.activeShellKind() == ShellRunner.ShellKind.POWERSHELL
            );
        }
    }
}

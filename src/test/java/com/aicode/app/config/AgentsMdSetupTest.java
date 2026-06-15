package com.aicode.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentsMdSetupTest {
    @TempDir
    Path workspace;

    @Test
    void detectsMissingAgentsMd() {
        assertFalse(AgentsMdSetup.hasAgentsMd(workspace));
    }

    @Test
    void writesTemplateToWorkspaceRoot() throws Exception {
        AgentsMdTemplate template = AgentsMdTemplates.find("minimal").orElseThrow();
        AgentsMdSetup.writeTemplate(workspace, template);

        Path target = workspace.resolve("AGENTS.md");
        assertTrue(Files.isRegularFile(target));
        assertEquals(template.content(), Files.readString(target));
        assertTrue(AgentsMdSetup.hasAgentsMd(workspace));
    }

    @Test
    void rejectsOverwrite() {
        AgentsMdTemplate template = AgentsMdTemplates.defaultTemplate();
        assertDoesNotThrow(() -> AgentsMdSetup.writeTemplate(workspace, template));
        assertThrows(Exception.class, () -> AgentsMdSetup.writeTemplate(workspace, template));
    }
}

package com.aicode.app.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentsMdTemplatesTest {
    @Test
    void loadsAllPresetTemplates() {
        assertEquals(7, AgentsMdTemplates.all().size());
        assertTrue(AgentsMdTemplates.all().stream().allMatch(t -> !t.content().isBlank()));
        assertTrue(AgentsMdTemplates.all().stream().allMatch(t -> !t.label().isBlank()));
    }

    @Test
    void findsTemplateById() {
        assertTrue(AgentsMdTemplates.find("java-maven").isPresent());
        assertTrue(AgentsMdTemplates.find("java-maven").orElseThrow().content().contains("Maven"));
        assertTrue(AgentsMdTemplates.find("missing").isEmpty());
    }

    @Test
    void defaultTemplateIsGeneral() {
        assertEquals("general", AgentsMdTemplates.defaultTemplate().id());
    }
}

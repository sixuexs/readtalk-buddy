package com.backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2-7 焦点注入工具单测（纯静态，无 LLM / 无 Spring）。
 */
class FocusInjectionTest {

    @Test
    void coachSectionContainsDefinitionAndFeed() {
        String section = SimulationService.coachSection("emotion_naming");
        assertTrue(section.contains("【本场训练焦点】"));
        assertTrue(section.contains("情绪命名"));
        assertTrue(section.contains("喂球方式"));
        assertTrue(section.contains("绝不点破"));
    }

    @Test
    void coachSectionEmptyForUnknownKey() {
        assertEquals("", SimulationService.coachSection("nope"));
    }

    @Test
    void normalizeFocus() {
        assertNull(SimulationService.normalizeFocus(null));
        assertNull(SimulationService.normalizeFocus("  "));
        assertNull(SimulationService.normalizeFocus("not_a_skill"));
        assertEquals("silence_handling", SimulationService.normalizeFocus("silence_handling"));
    }
}

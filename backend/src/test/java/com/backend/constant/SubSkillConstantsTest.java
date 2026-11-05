package com.backend.constant;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 子技能词典完整性验证（P1-1，纯单测，无 Spring 上下文）。
 */
class SubSkillConstantsTest {

    @Test
    void allHas16Entries() {
        assertEquals(16, SubSkillConstants.ALL.size());
        Set<String> keys = SubSkillConstants.ALL.stream()
                .map(SubSkillConstants.SubSkill::key).collect(Collectors.toSet());
        assertEquals(16, keys.size(), "key 必须唯一");
        assertTrue(keys.stream().allMatch(k -> k.matches("[a-z][a-z_]*[a-z]")),
                "短名必须为 snake_case（C 文档 §0.2 纪律）");
    }

    @Test
    void dimensionDistributionMatchesDesignDoc() {
        assertEquals(3, SubSkillConstants.byDimension(SubSkillConstants.DIM_CLARITY).size());
        assertEquals(3, SubSkillConstants.byDimension(SubSkillConstants.DIM_LOGICALITY).size());
        assertEquals(3, SubSkillConstants.byDimension(SubSkillConstants.DIM_EMPATHY_LISTENING).size());
        assertEquals(4, SubSkillConstants.byDimension(SubSkillConstants.DIM_INTERACTIVITY).size());
        assertEquals(3, SubSkillConstants.byDimension(SubSkillConstants.DIM_RELAXATION).size());
    }

    @Test
    void isValidWhitelist() {
        assertTrue(SubSkillConstants.isValid("emotion_naming"));
        assertTrue(SubSkillConstants.isValid("non_defensive"));
        assertFalse(SubSkillConstants.isValid("emotionnaming"));
        assertFalse(SubSkillConstants.isValid("EMOTION_NAMING"));
        assertFalse(SubSkillConstants.isValid(null));
        assertFalse(SubSkillConstants.isValid(""));
    }

    @Test
    void resolveKeyAcceptsChineseNames() {
        assertEquals("active_questioning", SubSkillConstants.resolveKey("主动提问"));
        assertEquals("turn_giving", SubSkillConstants.resolveKey("发言权让渡"));
        assertEquals("emotion_naming", SubSkillConstants.resolveKey(" emotion_naming "));
        assertNull(SubSkillConstants.resolveKey("不存在的名"));
        assertNull(SubSkillConstants.resolveKey(null));
    }

    @Test
    void promptBlockCoversAllKeysAndGroups() {
        String block = SubSkillConstants.promptBlock();
        for (SubSkillConstants.SubSkill s : SubSkillConstants.ALL) {
            assertTrue(block.contains("- " + s.key() + " " + s.cnName()),
                    "promptBlock 缺子技能行: " + s.key());
        }
        for (String dim : Set.of(SubSkillConstants.DIM_CLARITY, SubSkillConstants.DIM_LOGICALITY,
                SubSkillConstants.DIM_EMPATHY_LISTENING, SubSkillConstants.DIM_INTERACTIVITY,
                SubSkillConstants.DIM_RELAXATION)) {
            assertTrue(block.contains("### " + dim + " 子技能"), "promptBlock 缺维度分组: " + dim);
        }
    }

    @Test
    void recommendationForEveryEntryIsComplete() {
        for (SubSkillConstants.SubSkill s : SubSkillConstants.ALL) {
            SubSkillConstants.Recommendation r = SubSkillConstants.recommendationFor(s.key());
            assertNotNull(r);
            assertEquals(s.key(), r.subSkill());
            assertFalse(r.theme().isBlank());
            assertFalse(r.personality().isBlank());
            assertFalse(r.feed().isBlank());
        }
        assertNull(SubSkillConstants.recommendationFor("nope"));
    }

    @Test
    void everyEntryHasFullDictionaryFields() {
        for (SubSkillConstants.SubSkill s : SubSkillConstants.ALL) {
            assertFalse(s.cnName().isBlank());
            assertFalse(s.definition().isBlank());
            assertFalse(s.signal().isBlank());
            assertFalse(s.criteria().isBlank());
            assertFalse(SubSkillConstants.defaultInstruction(s.key()).isEmpty());
        }
    }

    @Test
    void defaultTaskTitlesTemplated() {
        assertEquals("完成2场针对「情绪命名」的模拟训练",
                SubSkillConstants.defaultSimTaskTitle("emotion_naming", 2));
        assertEquals("本周在真实对话中运用1次「复述确认」",
                SubSkillConstants.defaultRealTaskTitle("paraphrase", 1));
    }
}

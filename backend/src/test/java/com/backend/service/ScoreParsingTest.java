package com.backend.service;

import com.backend.constant.SubSkillConstants;
import com.backend.document.ConversationDocument;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P1-3 子技能层宽容解析单测（无 LLM、无 Spring 上下文）。
 */
class ScoreParsingTest {

    @Test
    void completeSixteenKeysParsed() {
        Map<String, Object> subs = new HashMap<>();
        for (SubSkillConstants.SubSkill s : SubSkillConstants.ALL) {
            subs.put(s.key(), 75);
        }
        Map<String, Object> scoreData = new HashMap<>();
        scoreData.put("subScores", subs);

        Map<String, Integer> parsed = SimulationService.parseSubScores(scoreData);
        assertEquals(16, parsed.size());
        assertEquals(75, parsed.get("emotion_naming"));
    }

    @Test
    void missingWholeSubScoresGivesAllNullShape() {
        Map<String, Integer> parsed = SimulationService.parseSubScores(Map.of());
        assertEquals(16, parsed.size());
        assertTrue(parsed.values().stream().allMatch(v -> v == null));
    }

    @Test
    void missingKeysAndBadValuesBecomeNull() {
        Map<String, Object> subs = new HashMap<>();
        subs.put("emotion_naming", 42);
        subs.put("paraphrase", "abc");      // 非数 → null
        subs.put("point_first", null);      // 显式 null → null
        Map<String, Object> scoreData = new HashMap<>();
        scoreData.put("subScores", subs);

        Map<String, Integer> parsed = SimulationService.parseSubScores(scoreData);
        assertEquals(16, parsed.size());
        assertEquals(42, parsed.get("emotion_naming"));
        assertNull(parsed.get("paraphrase"));
        assertNull(parsed.get("point_first"));
        assertNull(parsed.get("filler_control")); // 缺键 → null
    }

    @Test
    void outOfRangeClamped() {
        Map<String, Object> scoreData = Map.of("subScores",
                Map.of("filler_control", 120, "less_hedging", -5));
        Map<String, Integer> parsed = SimulationService.parseSubScores(scoreData);
        assertEquals(100, parsed.get("filler_control"));
        assertEquals(0, parsed.get("less_hedging"));
    }

    @Test
    void unknownKeyDoesNotBreakShape() {
        Map<String, Object> scoreData = Map.of("subScores",
                Map.of("emotionNaming", 60, "emotion_naming", 55));
        Map<String, Integer> parsed = SimulationService.parseSubScores(scoreData);
        assertEquals(16, parsed.size());
        assertEquals(55, parsed.get("emotion_naming"));
    }

    @Test
    void evidenceBadEntriesDroppedAndCapped() {
        Map<String, Object> good = new HashMap<>();
        good.put("subSkill", "emotion_naming");
        good.put("quote", "嗯嗯");
        good.put("issue", "未回应情绪");
        good.put("rewrite", "听起来你挺失落的");
        good.put("messageOrder", 12);
        good.put("polarity", "negative");

        Map<String, Object> noQuote = new HashMap<>();
        noQuote.put("subSkill", "paraphrase");
        noQuote.put("polarity", "positive");

        Map<String, Object> badSubSkill = new HashMap<>();
        badSubSkill.put("subSkill", "foo_bar");
        badSubSkill.put("quote", "x");

        Map<String, Object> noPolarity = new HashMap<>();
        noPolarity.put("subSkill", "turn_giving");
        noPolarity.put("quote", "你觉得呢");

        StringBuilder longQuote = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            longQuote.append('长');
        }
        Map<String, Object> longItem = new HashMap<>();
        longItem.put("subSkill", "self_disclosure");
        longItem.put("quote", longQuote.toString());

        // 5 条输入 → 上限 4；且坏项（noQuote/badSubSkill）被丢弃，只剩 3 条有效
        List<ConversationDocument.EvidenceItem> items = SimulationService.parseEvidence(
                List.of(good, noQuote, badSubSkill, noPolarity, longItem));

        assertEquals(3, items.size());
        assertEquals(12, items.get(0).getMessageOrder());
        assertEquals("negative", items.get(1).getPolarity());   // 缺省降级 negative
        assertEquals(60, items.get(2).getQuote().length());     // 截断 60
    }

    @Test
    void evidenceMissingOrNotListGivesEmpty() {
        assertTrue(SimulationService.parseEvidence(null).isEmpty());
        assertTrue(SimulationService.parseEvidence("garbage").isEmpty());
        assertTrue(SimulationService.parseEvidence(Map.of()).isEmpty());
    }
}

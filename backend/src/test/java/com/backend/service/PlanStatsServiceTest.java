package com.backend.service;

import com.backend.document.ConversationDocument;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P2-2 处方规则聚合单测（纯数据构造，无 Spring / 无 LLM）。
 */
class PlanStatsServiceTest {

    private final PlanStatsService svc = new PlanStatsService();

    private ConversationDocument conv(String id, String theme, LocalDateTime createdAt,
                                      Map<String, Integer> subScores,
                                      List<ConversationDocument.EvidenceItem> evidence) {
        ConversationDocument d = new ConversationDocument();
        d.setId(id);
        d.setTheme(theme);
        d.setCreatedAt(createdAt);
        d.setUpdatedAt(createdAt);
        d.setScore(60);
        ConversationDocument.Evaluation ev = new ConversationDocument.Evaluation();
        ev.setSubScores(subScores);
        ev.setEvidence(evidence);
        d.setEvaluation(ev);
        return d;
    }

    private Map<String, Integer> subs(Object... kv) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], (Integer) kv[i + 1]);
        }
        return m;
    }

    @Test
    void insufficientWhenNoSamples() {
        var stats = svc.compute(List.of());
        assertTrue(stats.insufficient());
        var s1 = conv("a", "日常闲聊", LocalDateTime.now(), new HashMap<>(), null); // subScores 全空
        var stats2 = svc.compute(List.of(s1));
        assertTrue(stats2.insufficient());
    }

    @Test
    void weakestAndFocusSelection() {
        LocalDateTime t = LocalDateTime.now();
        List<ConversationDocument> desc = List.of(
                conv("s5", "日常闲聊", t.minusDays(1), subs("emotion_naming", 38, "active_questioning", 85), List.of()),
                conv("s4", "读书交流", t.minusDays(2), subs("emotion_naming", 42, "active_questioning", 88), List.of()),
                conv("s3", "面试演练", t.minusDays(3), subs("emotion_naming", 50, "active_questioning", 80), List.of()));
        var stats = svc.compute(desc);
        assertFalse(stats.insufficient());
        assertEquals("emotion_naming", stats.focusSubSkills().get(0));
        assertEquals(3, stats.weakest().get(0).samples());
        assertTrue(stats.weakest().get(0).avg() <= 44);
        // 近 30 天练过的主题不在 gaps；未练的在
        assertTrue(stats.themeGaps().contains("读后感分享"));
        assertFalse(stats.themeGaps().contains("日常闲聊"));
    }

    @Test
    void trendingDropPromotesFocus() {
        LocalDateTime t = LocalDateTime.now();
        List<ConversationDocument> desc = new ArrayList<>();
        // 近 4 场：emotion_naming 低（weakest[0]）；active_questioning 前高后低（delta≤-8，样本≥3）
        int[][] aq = {{40, 85}, {40, 85}, {40, 50}, {40, 45}, {40, 45}}; // [emotion, active_q] 时间正序 5 场：emotion 恒低，active_q 显著下滑
        for (int i = 0; i < aq.length; i++) {
            desc.add(0, conv("x" + i, "日常闲聊", t.minusDays(i + 1),
                    subs("emotion_naming", aq[i][0], "active_questioning", aq[i][1]), List.of()));
        }
        var stats = svc.compute(desc);
        // active_questioning 下滑翻转为主焦点
        assertEquals("active_questioning", stats.focusSubSkills().get(0));
        assertEquals(2, stats.focusSubSkills().size());
        assertEquals("emotion_naming", stats.focusSubSkills().get(1));
    }

    @Test
    void negativeEvidenceCounting() {
        LocalDateTime t = LocalDateTime.now();
        ConversationDocument.EvidenceItem e1 = new ConversationDocument.EvidenceItem(
                "emotion_naming", 3, "嗯嗯", "未回应情绪", "", "negative");
        ConversationDocument.EvidenceItem e2 = new ConversationDocument.EvidenceItem(
                "emotion_naming", 4, "好的", "未回应情绪", "", "negative");
        ConversationDocument.EvidenceItem e3 = new ConversationDocument.EvidenceItem(
                "paraphrase", 2, "对对对", "空洞附和", "", "negative");
        ConversationDocument.EvidenceItem pos = new ConversationDocument.EvidenceItem(
                "active_questioning", 5, "你觉得呢", "", "", "positive");
        var c1 = conv("c1", "日常闲聊", t.minusDays(1), subs("emotion_naming", 40, "paraphrase", 55, "active_questioning", 80), List.of(e1, pos));
        var c2 = conv("c2", "读书交流", t.minusDays(2), subs("emotion_naming", 45, "paraphrase", 50, "active_questioning", 78), List.of(e2));
        var c3 = conv("c3", "面试演练", t.minusDays(3), subs("emotion_naming", 50, "paraphrase", 52, "active_questioning", 82), List.of(e3));
        var stats = svc.compute(List.of(c1, c2, c3));
        assertEquals("emotion_naming", stats.negativeEvidenceTop().get(0).subSkill());
        assertEquals(2, stats.negativeEvidenceTop().size());
    }
}

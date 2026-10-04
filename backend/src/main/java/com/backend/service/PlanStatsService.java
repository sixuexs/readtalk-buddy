package com.backend.service;

import com.backend.constant.SubSkillConstants;
import com.backend.document.ConversationDocument;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 处方规则聚合层（P2-2，设计文档 §3.3①）：感知用规则、纯函数、零 LLM。
 * 输入已评分会话（时间倒序，与 findAllByOrderByCreatedAtDesc 一致），输出统计与主攻焦点。
 */
@Service
public class PlanStatsService {

    /** 情景模拟主题清单（与 SimulationService.getConfig() 一致；避免循环依赖在此冗余一份）。 */
    public static final List<String> SIM_THEMES =
            List.of("初次见面", "读书交流", "读后感分享", "面试演练", "日常闲聊");

    public record Weakest(String subSkill, int avg, int samples) {}

    public record Trending(String subSkill, int delta, int samples) {}

    public record NegEvidence(String sessionId, String subSkill, String quote, String issue) {}

    public record PlanStats(List<Weakest> weakest, List<Trending> trending,
                            List<NegEvidence> negativeEvidenceTop, List<String> themeGaps,
                            List<String> focusSubSkills, boolean insufficient) {
        public static final PlanStats INSUFFICIENT =
                new PlanStats(List.of(), List.of(), List.of(), List.of(), List.of(), true);
    }

    /** trending 显著下滑阈值（升为主焦点的条件）。 */
    static final int TRENDING_PROMOTE_DELTA = -8;
    /** trending 记录阈值（|delta| 达到才列示）。 */
    static final int TRENDING_MIN_DELTA = 5;

    /**
     * 主入口。scoredDesc：全部已评分会话（evaluation 非空），时间倒序。
     */
    public PlanStats compute(List<ConversationDocument> scoredDesc) {
        // 窗口 = 近 RECENT_SESSIONS 场中有子技能评分的会话（新格式场次）
        List<ConversationDocument> window = new ArrayList<>();
        for (ConversationDocument d : scoredDesc) {
            if (window.size() >= SubSkillConstants.RECENT_SESSIONS) {
                break;
            }
            if (d.getEvaluation() != null && d.getEvaluation().getSubScores() != null) {
                window.add(d);
            }
        }

        // 每子技能：时间正序的含分值列表（window 是倒序 → 反向收集）
        Map<String, List<Integer>> valsAsc = new LinkedHashMap<>();
        for (SubSkillConstants.SubSkill s : SubSkillConstants.ALL) {
            valsAsc.put(s.key(), new ArrayList<>());
        }
        for (int i = window.size() - 1; i >= 0; i--) {
            Map<String, Integer> subs = window.get(i).getEvaluation().getSubScores();
            for (String key : valsAsc.keySet()) {
                Integer v = subs.get(key);
                if (v != null) {
                    valsAsc.get(key).add(v);
                }
            }
        }

        // weakest：样本≥MIN_SAMPLES，均值升序 top3
        List<Weakest> weakest = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : valsAsc.entrySet()) {
            List<Integer> vals = e.getValue();
            if (vals.size() >= SubSkillConstants.MIN_SAMPLES) {
                int avg = (int) Math.round(vals.stream().mapToInt(Integer::intValue).average().orElse(0));
                weakest.add(new Weakest(e.getKey(), avg, vals.size()));
            }
        }
        weakest.sort(Comparator.comparingInt(Weakest::avg).thenComparing(Weakest::subSkill));
        if (weakest.size() > 3) {
            weakest = new ArrayList<>(weakest.subList(0, 3));
        }
        if (weakest.isEmpty()) {
            return PlanStats.INSUFFICIENT;
        }

        // trending：样本≥4，正序切分 前≤5 / 后≤5，|delta|≥5 记录，按 delta 升序
        List<Trending> trending = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : valsAsc.entrySet()) {
            List<Integer> vals = e.getValue();
            int n = vals.size();
            if (n < 4) {
                continue;
            }
            int prevCount = Math.min(5, n / 2);
            List<Integer> prev = vals.subList(0, prevCount);
            List<Integer> rec = vals.subList(prevCount, n);
            int delta = (int) Math.round(rec.stream().mapToInt(Integer::intValue).average().orElse(0)
                    - prev.stream().mapToInt(Integer::intValue).average().orElse(0));
            if (Math.abs(delta) >= TRENDING_MIN_DELTA) {
                trending.add(new Trending(e.getKey(), delta, n));
            }
        }
        trending.sort(Comparator.comparingInt(Trending::delta));

        // negativeEvidenceTop：窗口内 negative 证据按子技能计数降序 top3（各带最新一条）
        Map<String, Integer> negCount = new HashMap<>();
        Map<String, NegEvidence> negLatest = new LinkedHashMap<>();
        for (ConversationDocument d : window) { // window 倒序 → 首个即最新
            List<ConversationDocument.EvidenceItem> evs = d.getEvaluation().getEvidence();
            if (evs == null) {
                continue;
            }
            for (ConversationDocument.EvidenceItem ev : evs) {
                if (!"negative".equals(ev.getPolarity()) || !SubSkillConstants.isValid(ev.getSubSkill())) {
                    continue;
                }
                negCount.merge(ev.getSubSkill(), 1, Integer::sum);
                negLatest.putIfAbsent(ev.getSubSkill(),
                        new NegEvidence(d.getId(), ev.getSubSkill(), ev.getQuote(), ev.getIssue()));
            }
        }
        List<NegEvidence> negTop = negCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(en -> negLatest.get(en.getKey()))
                .toList();

        // themeGaps：近 30 天无已评分场次的主题
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        Set<String> themesUsed = new HashSet<>();
        for (ConversationDocument d : scoredDesc) {
            if (d.getCreatedAt() != null && d.getCreatedAt().isAfter(since)) {
                themesUsed.add(d.getTheme());
            }
        }
        List<String> themeGaps = SIM_THEMES.stream().filter(t -> !themesUsed.contains(t)).toList();

        // 焦点选择：weakest[0]；若 trending[0] 显著下滑（≤-8 且样本≥3）则翻转
        List<String> focus = new ArrayList<>();
        Weakest primary = weakest.get(0);
        if (!trending.isEmpty()
                && trending.get(0).delta() <= TRENDING_PROMOTE_DELTA
                && trending.get(0).samples() >= 3
                && !trending.get(0).subSkill().equals(primary.subSkill())) {
            focus.add(trending.get(0).subSkill());
            focus.add(primary.subSkill());
        } else {
            focus.add(primary.subSkill());
            if (weakest.size() > 1 && weakest.get(1).avg() < 70) {
                focus.add(weakest.get(1).subSkill());   // 次焦点：同样偏弱的第二项
            }
        }
        return new PlanStats(weakest, trending, negTop, themeGaps, focus, false);
    }
}

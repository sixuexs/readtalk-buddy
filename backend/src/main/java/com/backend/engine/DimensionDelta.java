package com.backend.engine;

import com.backend.document.ConversationDocument.Evaluation;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 5维差异觉察引擎（骨架）。
 *
 * Design doc: guide_docs/社交能力评分 Rubric 设计文档.md §3-4
 * Read source: conversations.Evaluation（MongoDB，当前唯一已实现写源）。
 * self_relative not yet stored — will switch source when evaluation_record write is implemented.
 */
public class DimensionDelta {

    /** 差异方向：评分相对自评是偏高/一致/偏低 */
    public enum Direction { UP, FLAT, DOWN }

    /** 5维键值枚举 */
    public enum DimKey { CLARITY, LOGICALITY, EMPATHY_LISTENING, INTERACTIVITY, RELAXATION }

    /**
     * AI 评分 vs 自评基线差异（当前用占位基线 50）。
     *
     * 等 self_relative 落库后替换为
     * EvaluationRecord.getSelfRelative().get(key.toSnakeCase())。
     */
    public static Map<DimKey, Direction> delta(Evaluation aiScore) {
        Map<DimKey, Direction> result = new EnumMap<>(DimKey.class);

        result.put(DimKey.CLARITY, classify(aiScore.getClarity()));
        result.put(DimKey.LOGICALITY, classify(aiScore.getLogicality()));
        result.put(DimKey.EMPATHY_LISTENING, classify(aiScore.getEmpathyListening()));
        result.put(DimKey.INTERACTIVITY, classify(aiScore.getInteractivity()));
        result.put(DimKey.RELAXATION, classify(aiScore.getRelaxation()));

        return result;
    }

    /** Calculate delta against AI score for a single dimension */
    public static Direction delta(DimKey dim, Evaluation aiScore) {
        return classify(switch (dim) {
            case CLARITY -> aiScore.getClarity();
            case LOGICALITY -> aiScore.getLogicality();
            case EMPATHY_LISTENING -> aiScore.getEmpathyListening();
            case INTERACTIVITY -> aiScore.getInteractivity();
            case RELAXATION -> aiScore.getRelaxation();
        });
    }

    // --- relaxation-specific branch (T4) ---

    /**
     * Relaxation-specific delta with forbidden-word detection.
     *
     * <p>Unlike other dimensions where higher is always better, relaxation has a sweet spot
     * (mid-range optimal, extremes are warning signs — see Rubric B §3.5).
     *
     * <p><b>当前实现</b>（硬阈值模式，self_relative 未落库）：
     * <ul>
     *   <li>score &lt; 40 + 无禁用词 → DOWN（僵硬/退缩）</li>
     *   <li>score ≥ 70 + 有禁用词 → DOWN（过分随意/油滑），禁用词生效</li>
     *   <li>其他 → 标准 classify（对占位基线 50）</li>
     * </ul>
     *
     * <p><b>目标实现</b>（采信自评文案分支，需 self_relative 落库后启用）：
     * 以用户自评的 relaxation 值为基线（而非硬阈值 40/70），
     * 在自评基线上叠加禁用词惩罚。当前硬阈值是降级方案。
     *
     * @param aiScore      the AI evaluation (uses {@link Evaluation#getRelaxation()})
     * @param messageTexts user's message content strings to scan for forbidden language
     * @return Direction with relaxation-specific reasoning baked in
     */
    public static Direction relaxationDelta(Evaluation aiScore, List<String> messageTexts) {
        int score = aiScore.getRelaxation();
        boolean hasForbidden = DisableWordMatcher.matches(messageTexts);

        // Too casual: score is high but language is flippant
        if (hasForbidden && score >= 70) {
            return Direction.DOWN; // too casual  sweet spot exceeded on the high side
        }

        // Too stiff: score is very low and no forbidden words to explain it
        if (score < 40) {
            return Direction.DOWN; // too stiff  sweet spot not reached
        }

        return classify(score);
    }

    /**
     * Returns the fraction of messages that contain at least one forbidden word.
     *
     * @param messages user message content strings; null or empty returns 0.0
     * @return ratio in [0.0, 1.0]
     */
    public static double forbiddenWordRate(List<String> messages) {
        if (messages == null || messages.isEmpty()) return 0.0;
        long count = messages.stream().filter(msg ->
                DisableWordMatcher.PATTERNS.stream().anyMatch(msg::contains)).count();
        return (double) count / messages.size();
    }

    /**
     * Matcher for overly-casual / flippant language patterns.
     * FIXME: current list is placeholder  final list should come from design doc or AI-generated.
     */
    public static class DisableWordMatcher {
        private static final List<String> PATTERNS = List.of(
                "哈哈哈笑死",
                "6", "666",
                "绝绝子",
                "栓Q",
                "yyds"
                // more patterns TBD
        );

        public static boolean matches(List<String> messages) {
            if (messages == null || messages.isEmpty()) return false;
            return messages.stream().anyMatch(msg ->
                    PATTERNS.stream().anyMatch(msg::contains));
        }
    }

    // --- internal ---

    /**
     * Placeholder baseline — 占的是 <b>self_relative 侧</b>（AI 评分 vs 用户自评基线）。
     *
     * <p>当前状态：self_relative 未落库（evaluation_record 写逻辑未实现），
     * 此处用常数 50 占位。AI 评分通常分布在 60-80，与 50 的差值为正，
     * 因此无自评时会<b>误触发 UP</b>方向信号——这是已知 placeholder 缺陷。
     *
     * <p>修正路径：等 {@code EvaluationRecord.getSelfRelative()} 可读后替换此常量。
     * 在此之前，所有 delta 结果仅作骨架示例，不可用于生产决策。
     */
    private static final int PLACEHOLDER_SELF_RELATIVE = 50;

    private static Direction classify(int score) {
        int diff = score - PLACEHOLDER_SELF_RELATIVE;
        if (diff >= 2) return Direction.UP;
        if (diff <= -2) return Direction.DOWN;
        return Direction.FLAT;
    }
}

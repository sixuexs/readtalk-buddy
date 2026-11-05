package com.backend.agent;

import com.backend.document.ConversationDocument;
import com.backend.document.UserProfileDocument;
import com.backend.repository.ConversationRepository;
import com.backend.repository.UserProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户画像 Agent 工具集
 */
@Component
public class UserProfileTools {

    private static final Logger log = LoggerFactory.getLogger(UserProfileTools.class);

    private final UserProfileRepository profileRepo;
    private final ConversationRepository conversationRepo;
    private final ChatClient chatClient;

    public UserProfileTools(UserProfileRepository profileRepo,
                            ConversationRepository conversationRepo,
                            ChatClient.Builder chatClientBuilder) {
        this.profileRepo = profileRepo;
        this.conversationRepo = conversationRepo;
        this.chatClient = chatClientBuilder.build();
    }

    @Tool(description = "获取用户社交能力画像，包含各维度平均分、趋势数据、优劣势、提升计划")
    public Map<String, Object> getProfile() {
        var profile = profileRepo.findDefault().orElse(null);
        if (profile == null) {
            return Map.of("exists", false, "message", "暂无评估数据，先完成几次情景模拟对话吧");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exists", true);
        result.put("avgClarity", profile.getAvgClarity());
        result.put("avgLogicality", profile.getAvgLogicality());
        result.put("avgEmpathyListening", profile.getAvgEmpathyListening());
        result.put("avgInteractivity", profile.getAvgInteractivity());
        result.put("avgRelaxation", profile.getAvgRelaxation());
        result.put("overallScore", profile.getOverallScore());
        result.put("totalSessions", profile.getTotalSessions());
        result.put("assessment", profile.getAssessment());
        result.put("topStrengths", profile.getTopStrengths());
        result.put("topWeaknesses", profile.getTopWeaknesses());
        result.put("improvementPlan", profile.getImprovementPlan());
        result.put("weeklyGoals", profile.getWeeklyGoals());
        result.put("scoreHistory", profile.getScoreHistory());
        return result;
    }

    /** 加载所有已评分会话（时间倒序）。 */
    private List<ConversationDocument> loadScoredConversations() {
        return conversationRepo.findAllByOrderByCreatedAtDesc()
                .stream()
                .filter(d -> d.getScore() != null && d.getEvaluation() != null)
                .collect(Collectors.toList());
    }

    /**
     * 纯规则统计刷新：5 维均分 / 综合分 / scoreHistory / totalSessions / 子技能聚合 / streak。零 LLM 调用。
     * <p>绝不触碰 assessment / topStrengths / topWeaknesses / improvementPlan /
     * weeklyGoals / weeklyGoalsStatus，因此可安全地在每次评分事件后调用而不会重置打卡进度。
     *
     * @param fromScoring streak 仅由评分事件更新（勘误 #5）；手动/评估路径传 false，防误重置连续性
     */
    public UserProfileDocument refreshStats(boolean fromScoring) {
        List<ConversationDocument> scored = loadScoredConversations();
        UserProfileDocument profile = profileRepo.findDefault().orElseGet(UserProfileDocument::new);
        profile.setId("default");
        if (profile.getUserId() == null) {
            profile.setUserId(1L);  // 单用户模式，与 /api/user 默认 userId 对齐
        }
        if (!scored.isEmpty()) {
            double avgClarity = scored.stream().mapToInt(d -> d.getEvaluation().getClarity()).average().orElse(0);
            double avgLogicality = scored.stream().mapToInt(d -> d.getEvaluation().getLogicality()).average().orElse(0);
            double avgEmpathyListening = scored.stream().mapToInt(d -> d.getEvaluation().getEmpathyListening()).average().orElse(0);
            double avgInteractivity = scored.stream().mapToInt(d -> d.getEvaluation().getInteractivity()).average().orElse(0);
            double avgRelaxation = scored.stream().mapToInt(d -> d.getEvaluation().getRelaxation()).average().orElse(0);
            profile.setAvgClarity((int) avgClarity);
            profile.setAvgLogicality((int) avgLogicality);
            profile.setAvgEmpathyListening((int) avgEmpathyListening);
            profile.setAvgInteractivity((int) avgInteractivity);
            profile.setAvgRelaxation((int) avgRelaxation);
            profile.setOverallScore((int) ((avgClarity + avgLogicality + avgEmpathyListening + avgInteractivity + avgRelaxation) / 5.0));
            profile.setScoreHistory(scored.stream()
                    .map(d -> new UserProfileDocument.ScoreRecord(
                            d.getId(), d.getTheme(),
                            d.getScore(), d.getEvaluation().getClarity(),
                            d.getEvaluation().getLogicality(), d.getEvaluation().getEmpathyListening(),
                            d.getEvaluation().getInteractivity(), d.getEvaluation().getRelaxation(),
                            d.getUpdatedAt()))
                    .collect(Collectors.toList()));
            profile.setTotalSessions(scored.size());

            // ── P1-4 子技能聚合：窗口=近 RECENT_SESSIONS 场已评分会话，均值跳过 null（无观察机会） ──
            List<ConversationDocument> window = scored.stream()
                    .limit(com.backend.constant.SubSkillConstants.RECENT_SESSIONS)
                    .toList();
            Map<String, Integer> subAverages = new LinkedHashMap<>();
            Map<String, Integer> subSamples = new LinkedHashMap<>();
            java.time.LocalDateTime now = LocalDateTime.now();
            for (com.backend.constant.SubSkillConstants.SubSkill s : com.backend.constant.SubSkillConstants.ALL) {
                List<Integer> vals = new ArrayList<>();
                for (ConversationDocument d : window) { // window 为时间倒序 → vals 新→旧
                    Map<String, Integer> subs = d.getEvaluation().getSubScores();
                    if (subs != null) {
                        Integer v = subs.get(s.key());
                        if (v != null) {
                            vals.add(v);
                        }
                    }
                }
                subSamples.put(s.key(), vals.size());
                int avg = vals.isEmpty() ? 0
                        : (int) Math.round(vals.stream().mapToInt(Integer::intValue).average().orElse(0));
                subAverages.put(s.key(), vals.isEmpty() ? null : avg);
                // ── P3-3 徽章判定：近 3 场含分移动均值 ≥80→Lv1，≥90→Lv2（upsert 不回收） ──
                if (vals.size() >= 3) {
                    if (profile.getBadges() == null) {
                        profile.setBadges(new ArrayList<>());
                    }
                    double last3 = vals.subList(0, 3).stream().mapToInt(Integer::intValue).average().orElse(0);
                    if (applyBadge(profile.getBadges(), s.key(), last3, now)) {
                        log.info("子技能徽章达成: {} Lv{}", s.key(),
                                last3 >= com.backend.constant.SubSkillConstants.MASTERY_L2 ? 2 : 1);
                    }
                }
            }
            profile.setSubSkillAverages(subAverages);
            profile.setSubSkillSampleCount(subSamples);
        }

        // ── P1-4 streak：仅评分事件触发且当日未记 ──
        if (fromScoring) {
            java.time.LocalDate today = java.time.LocalDate.now();
            if (!today.equals(profile.getLastActiveDate())) {
                int[] st = computeStreak(profile.getLastActiveDate(), profile.getStreakDays(),
                        profile.getBestStreak(), today);
                profile.setStreakDays(st[0]);
                profile.setBestStreak(st[1]);
                profile.setLastActiveDate(today);
            }
        }
        profile.setLastUpdated(LocalDateTime.now());
        return profileRepo.save(profile);
    }

    /**
     * 训练连续天数纯函数（包级可见便于单测）：lastActive 为昨天 → current+1，否则重置 1；best 取历史最高。
     * 返回 {streakDays, bestStreak}。
     */
    static int[] computeStreak(java.time.LocalDate lastActive, int current, int best, java.time.LocalDate today) {
        int streak = (lastActive != null && lastActive.equals(today.minusDays(1))) ? current + 1 : 1;
        return new int[]{streak, Math.max(streak, best)};
    }

    /**
     * 徽章 upsert 纯函数（P3-3，勘误 #6；包级可见便于单测）：
     * level = last3≥90→2 / ≥80→1 / 其余 0（0 不操作，不回收既有徽章）；
     * 已有记录只升不降；achievedAt=首次达成，lastUpgradedAt=最近升级。
     *
     * @return 是否发生新达成或升级
     */
    static boolean applyBadge(List<UserProfileDocument.BadgeRecord> badges, String subSkill,
                              double last3Avg, LocalDateTime now) {
        int level = last3Avg >= com.backend.constant.SubSkillConstants.MASTERY_L2 ? 2
                : last3Avg >= com.backend.constant.SubSkillConstants.MASTERY_L1 ? 1 : 0;
        if (level == 0) {
            return false;
        }
        for (UserProfileDocument.BadgeRecord b : badges) {
            if (subSkill.equals(b.getSubSkill())) {
                if (level > b.getLevel()) {
                    b.setLevel(level);
                    b.setLastUpgradedAt(now);
                    return true;
                }
                return false;
            }
        }
        badges.add(new UserProfileDocument.BadgeRecord(subSkill, level, now, now));
        return true;
    }

    @Tool(description = "基于用户历史评分数据，生成或更新用户能力评估和个性化提升计划")
    public Map<String, Object> assessAbility() {
        // 加载已评分会话
        List<ConversationDocument> scored = loadScoredConversations();

        if (scored.isEmpty()) {
            return Map.of("status", "no_data", "message", "暂无已评分的会话");
        }

        // 统计段复用纯规则刷新路径（评估刷新不动 streak，勘误 #5）
        UserProfileDocument profile = refreshStats(false);
        int totalSessions = scored.size();
        double avgClarity = profile.getAvgClarity();
        double avgLogicality = profile.getAvgLogicality();
        double avgEmpathyListening = profile.getAvgEmpathyListening();
        double avgInteractivity = profile.getAvgInteractivity();
        double avgRelaxation = profile.getAvgRelaxation();
        int overall = profile.getOverallScore();

        // 汇总所有评语，调用 AI 生成总体评估和提升计划
        String allComments = scored.stream()
                .map(d -> d.getTheme() + "：" + d.getEvaluation().getComment())
                .collect(Collectors.joining("\n"));

        String profilePrompt = String.format("""
                你是一位社交能力评估专家。根据以下用户历史情景模拟的评分记录，生成用户画像报告。

                ## 评分统计
                - 累计会话数：%d
                - 清晰度均分：%.0f
                - 逻辑思辨力均分：%.0f
                - 共情倾听均分：%.0f
                - 互动积极性均分：%.0f
                - 情绪松弛度均分：%.0f
                - 综合评分：%d

                ## 历史评语
                %s

                ## 输出要求
                以 JSON 格式返回：
                {
                  "assessment": "100字以内总体评价",
                  "topStrengths": ["优势1", "优势2"],
                  "topWeaknesses": ["短板1", "短板2"],
                  "improvementPlan": "200字以内个性化提升计划",
                  "weeklyGoals": ["每周目标1", "每周目标2", "每周目标3"]
                }
                只返回JSON。""",
                totalSessions, avgClarity, avgLogicality, avgEmpathyListening, avgInteractivity, avgRelaxation, overall, allComments);

        try {
            String result = chatClient.prompt().user(profilePrompt).call().content();
            @SuppressWarnings("unchecked")
            Map<String, Object> aiResult = new com.fasterxml.jackson.databind.ObjectMapper().readValue(result, Map.class);

            // 持久化评估文本（统计字段已由 refreshStats() 落库）
            profile.setAssessment((String) aiResult.get("assessment"));
            profile.setTopStrengths((List<String>) aiResult.get("topStrengths"));
            profile.setTopWeaknesses((List<String>) aiResult.get("topWeaknesses"));
            profile.setImprovementPlan((String) aiResult.get("improvementPlan"));
            // P0-2 打卡进度保护：仅当从未生成过周目标时才写入 goals 并初始化 status；
            // 已有 weeklyGoals 时不覆盖（保留原目标与打卡进度），根治"评分/刷新即清零"缺陷。
            List<String> existingGoals = profile.getWeeklyGoals();
            if (existingGoals == null || existingGoals.isEmpty()) {
                List<String> newGoals = (List<String>) aiResult.get("weeklyGoals");
                profile.setWeeklyGoals(newGoals);
                profile.setWeeklyGoalsStatus(newGoals == null ? new ArrayList<>()
                        : new ArrayList<>(java.util.Collections.nCopies(newGoals.size(), false)));
            }
            profile.setLastUpdated(LocalDateTime.now());
            profileRepo.save(profile);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("overallScore", overall);
            response.put("avgClarity", (int) avgClarity);
            response.put("avgLogicality", (int) avgLogicality);
            response.put("avgEmpathyListening", (int) avgEmpathyListening);
            response.put("avgInteractivity", (int) avgInteractivity);
            response.put("avgRelaxation", (int) avgRelaxation);
            response.put("totalSessions", totalSessions);
            response.put("assessment", aiResult.get("assessment"));
            response.put("topStrengths", aiResult.get("topStrengths"));
            response.put("topWeaknesses", aiResult.get("topWeaknesses"));
            response.put("improvementPlan", aiResult.get("improvementPlan"));
            response.put("weeklyGoals", profile.getWeeklyGoals());
            return response;

        } catch (Exception e) {
            return Map.of("status", "error", "message", "生成画像失败: " + e.getMessage());
        }
    }
}

package com.backend.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 用户画像 —— 聚合所有历史评分数据，生成能力评估和提升计划
 */
@Document("user_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDocument {

    @Id
    private String id;                              // "default"（单用户模式）

    // 各维度加权平均分（5维评分）
    private int avgClarity;                         // 平均清晰度
    private int avgLogicality;                      // 平均逻辑性
    private int avgEmpathyListening;                // 平均共情倾听
    private int avgInteractivity;                   // 平均互动性
    private int avgRelaxation;                      // 平均松弛感
    private int overallScore;                       // 综合评分

    // 趋势数据（按时间排序的评分记录摘要）
    private List<ScoreRecord> scoreHistory = new ArrayList<>();

    // AI 生成的能力评估
    private String assessment;                      // 总体评价
    private List<String> topStrengths;              // 突出优势
    private List<String> topWeaknesses;             // 主要短板

    // AI 生成的提升计划
    private String improvementPlan;                 // 专属提升路线
    private List<String> weeklyGoals;               // 每周目标
    private List<Boolean> weeklyGoalsStatus = new ArrayList<>();  // 周目标打卡状态（与 weeklyGoals 下标对齐）
    private List<GoalSubmission> weeklyGoalSubmissions = new ArrayList<>();  // P0-3 打卡附言（index 与 weeklyGoals 对齐）

    private int totalSessions;                      // 累计会话数

    /** P1-4 子技能聚合：短名 → 近10场含分均值（无样本为 null；词典见 SubSkillConstants） */
    private Map<String, Integer> subSkillAverages;
    /** P1-4 各子技能含分场次数（样本约束/前端灰态依据） */
    private Map<String, Integer> subSkillSampleCount;
    /** P3 徽章达成记录（存事实，不回收） */
    private List<BadgeRecord> badges = new ArrayList<>();
    /** 训练连续性（streak，仅评分事件更新，勘误 #5） */
    private int streakDays;
    private int bestStreak;
    private java.time.LocalDate lastActiveDate;

    // 名片字段
    private Long userId;                            // 用户ID
    private String displayName;                     // 名片显示名
    private String biography;                       // 个人简介
    private String status;                          // 当前状态签名（如"正在读《三体》"）
    private String avatar;                          // 头像（emoji 字符，如 🐱）
    private String personality;                     // 性格描述
    private List<String> interests = new ArrayList<>();  // 兴趣爱好
    private List<String> labels = new ArrayList<>();     // 标签

    private LocalDateTime lastUpdated;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreRecord {
        private String sessionId;
        private String theme;
        private int score;
        private int clarity;
        private int logicality;
        private int empathyListening;
        private int interactivity;
        private int relaxation;
        private LocalDateTime scoredAt;
    }

    /** P0-3：周目标打卡附言——一次勾选配一段复盘，index 与 weeklyGoals 对齐。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalSubmission {
        private int index;
        private String text;
        private LocalDateTime at;
    }

    /** P3：子技能掌握徽章（近3场含分移动均值 ≥80→Lv1，≥90→Lv2；upsert 语义见勘误 #6）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BadgeRecord {
        private String subSkill;
        private int level;
        private LocalDateTime achievedAt;
        private LocalDateTime lastUpgradedAt;
    }
}

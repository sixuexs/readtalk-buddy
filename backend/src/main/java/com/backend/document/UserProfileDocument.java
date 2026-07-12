package com.backend.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // 各维度加权平均分
    private int avgExpression;                      // 平均表达力
    private int avgAffinity;                        // 平均亲和力
    private int avgLogic;                           // 平均逻辑性
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

    private int totalSessions;                      // 累计会话数
    private LocalDateTime lastUpdated;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreRecord {
        private String sessionId;
        private String theme;
        private int score;
        private int expression;
        private int affinity;
        private int logic;
        private LocalDateTime scoredAt;
    }
}

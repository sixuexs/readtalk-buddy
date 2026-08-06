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

    private int totalSessions;                      // 累计会话数

    // 名片字段
    private Long userId;                            // 用户ID
    private String displayName;                     // 名片显示名
    private String biography;                       // 个人简介
    private String status;                          // 当前状态签名（如"正在读《三体》"）

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
}

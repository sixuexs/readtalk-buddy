package com.backend.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话摘要 —— 用于社交记录列表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummary {
    private String sessionId;       // 会话 UUID
    private String theme;           // 场景主题
    private String personality;     // 角色性格
    private Integer score;          // 社交能力评分 (0-100)，null 表示未评分
    private int messageCount;       // 消息总数
    private long lastActivity;      // 最后活动时间（毫秒时间戳）
}

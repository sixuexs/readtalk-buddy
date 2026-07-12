package com.backend.agent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Agent 事件基类 —— Agent 间松耦合通信基础设施
 * <p>
 * 使用 Spring ApplicationEventPublisher 发布，
 * 各 Agent 通过 @EventListener 订阅感兴趣的事件。
 */
public abstract class AgentEvent {

    private final String eventId = UUID.randomUUID().toString();
    private final LocalDateTime timestamp = LocalDateTime.now();

    public String eventId() { return eventId; }
    public LocalDateTime timestamp() { return timestamp; }

    // ──── 具体事件类型 ────

    /** 评分完成事件 → UserProfileAgent 更新画像 */
    public static class ScoringCompleted extends AgentEvent {
        private final String sessionId;
        private final int score;

        public ScoringCompleted(String sessionId, int score) {
            this.sessionId = sessionId;
            this.score = score;
        }

        public String sessionId() { return sessionId; }
        public int score() { return score; }
    }

    /** 新联系人添加事件 → RelationAgent 初始化亲密度 */
    public static class ContactAdded extends AgentEvent {
        private final String contactId;
        private final String name;

        public ContactAdded(String contactId, String name) {
            this.contactId = contactId;
            this.name = name;
        }

        public String contactId() { return contactId; }
        public String name() { return name; }
    }

    /** 用户画像更新事件 */
    public static class ProfileUpdated extends AgentEvent {
        private final String profileId;

        public ProfileUpdated(String profileId) {
            this.profileId = profileId;
        }

        public String profileId() { return profileId; }
    }
}

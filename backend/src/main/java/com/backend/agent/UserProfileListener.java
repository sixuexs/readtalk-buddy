package com.backend.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 用户画像事件监听器 —— 监听到评分完成事件后自动更新画像
 */
@Component
public class UserProfileListener {

    private static final Logger log = LoggerFactory.getLogger(UserProfileListener.class);
    private final UserProfileTools profileTools;

    public UserProfileListener(UserProfileTools profileTools) {
        this.profileTools = profileTools;
    }

    @EventListener
    public void onScoringCompleted(AgentEvent.ScoringCompleted event) {
        log.info("收到评分完成事件: sessionId={}, score={} — 自动更新用户画像",
                event.sessionId(), event.score());
        try {
            var result = profileTools.assessAbility();
            log.info("用户画像更新完成: {}", result);
        } catch (Exception e) {
            log.error("自动更新用户画像失败", e);
        }
    }
}

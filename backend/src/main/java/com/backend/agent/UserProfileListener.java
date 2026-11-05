package com.backend.agent;

import com.backend.service.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 用户画像事件监听器 —— 监听到评分完成事件后自动刷新能力统计。
 * <p>P0-2：不再触发 assessAbility（计划重生成），打卡进度不被评分重置。
 * <p>P2-4：追加处方任务自动回销（纯规则）。两段各自 try/catch 互不阻断。
 */
@Component
public class UserProfileListener {

    private static final Logger log = LoggerFactory.getLogger(UserProfileListener.class);
    private final UserProfileTools profileTools;
    private final PlanService planService;

    public UserProfileListener(UserProfileTools profileTools, PlanService planService) {
        this.profileTools = profileTools;
        this.planService = planService;
    }

    @EventListener
    public void onScoringCompleted(AgentEvent.ScoringCompleted event) {
        log.info("收到评分完成事件: sessionId={}, score={} — 刷新能力统计（零 LLM）",
                event.sessionId(), event.score());
        try {
            var stats = profileTools.refreshStats(true);
            log.info("能力统计刷新完成: overallScore={}, totalSessions={}（评估文本与周目标打卡不受影响）",
                    stats.getOverallScore(), stats.getTotalSessions());
        } catch (Exception e) {
            log.error("自动刷新能力统计失败", e);
        }
        try {
            planService.onSessionScored(event.sessionId());
        } catch (Exception e) {
            log.error("处方任务自动回销失败: sessionId={}", event.sessionId(), e);
        }
    }
}

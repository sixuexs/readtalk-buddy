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
 * 周报（P3-1，设计文档 §5.2）—— 周切换时对已归档处方的规则快照 + LLM 摘要 + 下周草稿。
 * 同一 userId + weekKey 至多一份（幂等）。
 */
@Document("weekly_reports")
@Data
@NoArgsConstructor
public class WeeklyReportDocument {

    @Id
    private String id;

    private Long userId;
    private String weekKey;
    private String planId;

    private Completion completion;
    /** 子技能变化：本周含分场均 vs 上周基线（仅两侧都有分的子技能） */
    private Map<String, Integer> deltas;
    /** 本周最佳正向证据 ≤2 条 */
    private List<Highlight> highlights = new ArrayList<>();

    private String aiSummary;
    /** LLM 产出的下周处方草稿（adopt 后冻结为 adoptedDraft） */
    private NextWeekDraft nextWeekDraft;
    private Boolean adopted = false;
    /** 勘误 #4：adopt 时冻结的草稿快照，周切换据此实例化 */
    private NextWeekDraft adoptedDraft;

    private LocalDateTime createdAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Completion {
        private int tasksTotal;
        private int tasksDone;
        private int tasksExpired;
        private int sessionsScored;
        private int focusSessions;
        private int submissions;
        private int submissionsAccepted;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Highlight {
        private String sessionId;
        private String subSkill;
        private String quote;
    }

    /** 下周草稿：结构与处方 diagnosis/tasks 一致（tasks 已过校验器）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NextWeekDraft {
        private String summary;
        private List<TrainingPlanDocument.Task> tasks;
    }
}

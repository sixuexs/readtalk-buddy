package com.backend.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 训练处方（P2）—— 能力提升模块的"计划"实体，按 ISO 周版本化。
 *
 * <p>同一 userId + weekKey 至多一个 {@link #STATUS_ACTIVE}；旧计划归档不删。
 * schema 与设计文档《能力提升模块重构设计》§3.1/§3.2 一一对应，含勘误 #2 的
 * creditedSessionIds / doneAt 两字段。契约冻结：改结构必须回写设计文档。
 */
@Document("training_plans")
@Data
@NoArgsConstructor
public class TrainingPlanDocument {

    @Id
    private String id;

    private Long userId;
    private String weekKey;                    // 2026-W38
    private LocalDate weekStart;
    private LocalDate weekEnd;
    private String status;                     // active | archived
    private String generatedBy;                // auto | manual | adopted

    private Diagnosis diagnosis;
    private List<Task> tasks = new ArrayList<>();
    private LocalDateTime generatedAt;

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_ARCHIVED = "archived";

    /** 本周诊断（规则聚合快照 + LLM 摘要）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Diagnosis {
        private List<String> focusSubSkills;   // 本周主攻 1~2 个子技能短名
        private String summary;                // ≤80字
        private Map<String, Object> stats;     // PlanStatsService 输出快照（原样存档，可溯源）
        private List<EvidenceRef> evidenceRefs;
    }

    /** 证据引用（诊断/任务溯源用）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceRef {
        private String sessionId;
        private String subSkill;
    }

    /** 训练任务。 */
    @Data
    @NoArgsConstructor
    public static class Task {
        private String id;                     // t1..tn
        private String type;                   // simulation | realworld | knowledge
        private String subSkill;               // 子技能短名（白名单）
        private String title;
        private String instruction;            // 微知识卡（≤120字做法指导）
        private String successCriteria;        // 人类可读完成标准
        private String verifyMode;             // auto | evidence
        private AutoRule autoRule;             // verifyMode=auto 必填
        private int target;
        private int progress;
        private String status;                 // pending | in_progress | done | expired
        private List<Submission> submissions = new ArrayList<>();
        private List<EvidenceRef> whyRefs;
        private LocalDateTime dueAt;
        /** 勘误 #2：已被计入进度的 sessionId（自动回销幂等去重） */
        private List<String> creditedSessionIds = new ArrayList<>();
        /** 勘误 #2：完成时刻 */
        private LocalDateTime doneAt;
    }

    public static final String TASK_PENDING = "pending";
    public static final String TASK_IN_PROGRESS = "in_progress";
    public static final String TASK_DONE = "done";
    public static final String TASK_EXPIRED = "expired";
    public static final String TYPE_SIMULATION = "simulation";
    public static final String TYPE_REALWORLD = "realworld";
    public static final String TYPE_KNOWLEDGE = "knowledge";
    public static final String VERIFY_AUTO = "auto";
    public static final String VERIFY_EVIDENCE = "evidence";

    /** 自动回销规则（纯判定，零 LLM）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AutoRule {
        public static final String KIND_SESSION_MIN = "session_subskill_min";
        public static final String KIND_FOCUS_COUNT = "focus_session_count";

        private String kind;
        private String subSkill;
        private Integer minScore;              // KIND_SESSION_MIN 用
        private Integer target;                // 与 Task.target 一致（冗余便于规则自包含）
    }

    /** evidence 类任务的提交记录（提交式打卡 + AI 点评）。 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Submission {
        private String text;
        private String aiFeedback;
        private boolean accepted;
        private LocalDateTime at;
    }
}

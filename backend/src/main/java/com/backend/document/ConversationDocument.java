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

@Document("conversation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDocument {

    @Id
    private String id;                     // sessionId (UUID)

    private String theme;                  // 场景主题
    private String personality;            // 角色性格
    private String systemPrompt;           // 完整的系统 prompt

    private Integer score;                 // 社交能力评分 (0-100)，null 表示未评分

    /**
     * 关联联系人 ID（读侧字段，供 E 模块深度/质量计算用）。
     *
     * 对齐 MongoDB contacts 的 String id（P0 统一：联系人以 MongoDB 为唯一真相源）。
     * null 表示本场模拟未绑定书友（纯能力训练），不参与亲密度计算。
     */
    private String relatedContactId;

    /**
     * 所属用户 ID（读侧字段）。
     *
     * TODO[多用户]: 写入路径补充此字段后，仓库查询补 userId 过滤。
     */
    private Long userId;

    /**
     * 会话来源：simulation（情景模拟）/ offline（线下沟通复盘）。
     * 旧文档为 null，按 simulation 处理（前端与统计均容错）。
     */
    private String origin;

    private Evaluation evaluation;         // 详细评分（维度分 + 评语 + 标签）

    /** 会后复盘自评（对应评价体系 self_state / self_comment） */
    private String selfState;              // 交流时的个人状态标签（顿号拼接，如"紧张、投入"）
    private String selfComment;            // 用户自评评语

    /** P1/P2 靶向训练（能力提升重构）：本场焦点子技能短名 + 开练前基线 + 关联处方任务 id；null = 自由训练 */
    private String focusSubSkill;
    private Integer focusBaseline;
    private String prescriptionTaskId;

    private List<MessageItem> messages = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 评分详情
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    /* 5维评分: clarity/清晰度, logicality/逻辑性, empathyListening/共情倾听, interactivity/互动性, relaxation/松弛感 */
    public static class Evaluation {
        private int clarity;
        private int logicality;
        private int empathyListening;
        private int interactivity;
        private int relaxation;
        private String comment;            // AI 评语
        private List<String> strengths;    // 优点标签
        private List<String> suggestions;  // 改进建议标签
        /** P1 子技能分：子技能短名 → 0-100；值可为 null（本场无观察机会，词典见 SubSkillConstants） */
        private Map<String, Integer> subScores;
        /** P1 证据链：0~4 条原话证据（quote/issue/rewrite） */
        private List<EvidenceItem> evidence;
    }

    /**
     * 子技能证据（P1）：用户原话片段 + 问题标注 + 改写示范。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceItem {
        private String subSkill;               // 子技能短名（SubSkillConstants 白名单）
        private Integer messageOrder;          // 原话消息序号（messages[].messageOrder）
        private String quote;                  // 用户原话逐字片段（≤60 字）
        private String issue;                  // 问题描述（negative 必填）
        private String rewrite;                // 更好的说法示范（negative 必填）
        private String polarity;               // negative | positive
    }

    /**
     * 内嵌消息子文档
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageItem {
        private int messageOrder;          // 会话内消息序号 (1, 2, 3...)
        private String role;               // "self" 或 "other"
        private String avatar;             // 头像地址（预留）
        private String content;            // 消息内容
        private long timestamp;            // 毫秒时间戳
    }
}

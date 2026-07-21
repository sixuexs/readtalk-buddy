package com.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 社交能力评估记录 — 雷达图 / 折线图 / 亲密度"深度·质量"分（仅 1 对 1）的数据源
 */
@Entity
@Table(name = "evaluation_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Integer source;                     // 1=real 2=simulation

    @Column(name = "scene_type", nullable = false)
    private String sceneType;                   // salon/one_on_one/library_encounter/sim_xxx

    @Column(name = "score_clarity", nullable = false)
    private Integer scoreClarity;               // 表达清晰度 0-100

    @Column(name = "score_logicality", nullable = false)
    private Integer scoreLogicality;            // 逻辑思辨力 0-100

    @Column(name = "score_empathy_listening", nullable = false)
    private Integer scoreEmpathyListening;      // 共情与倾听 0-100

    @Column(name = "score_interactivity", nullable = false)
    private Integer scoreInteractivity;         // 互动积极性 0-100

    @Column(name = "score_relaxation", nullable = false)
    private Integer scoreRelaxation;            // 情绪松弛度 0-100

    @Column(name = "score_overall", nullable = false)
    private Integer scoreOverall;               // AI加权总分 0-100

    @Column(name = "ai_analysis_text", columnDefinition = "TEXT")
    private String aiAnalysisText;              // AI整篇文字点评

    @Column(name = "self_relative", columnDefinition = "JSON")
    private String selfRelative;                // 每维相对上次 up/flat/down

    @Column(name = "self_state")
    private String selfState;                   // 状态标签

    @Column(name = "self_comment")
    private String selfComment;                 // 用户评语

    @Column(name = "related_contact_id")
    private Long relatedContactId;              // one_on_one填contact.id; salon/simulation为NULL

    @Column(name = "interaction_id", nullable = false)
    private Long interactionId;                 // ->interaction_meta.id

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

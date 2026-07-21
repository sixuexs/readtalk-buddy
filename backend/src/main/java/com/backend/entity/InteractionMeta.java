package com.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 交流记录元数据 — 亲密度时效 / 频率来源；biz_id 与 MongoDB conversation_detail._id 对应
 */
@Entity
@Table(name = "interaction_meta")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InteractionMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "biz_id", nullable = false, unique = true)
    private String bizId;                       // 业务幂等键=Mongo明细_id

    @Column(name = "user_id", nullable = false)
    private Long userId;                        // 所属用户->user.id

    @Column(name = "type", nullable = false)
    private Integer source;                     // 1=real 2=simulation (DB column: type)

    @Column(name = "scene_type", nullable = false)
    private String sceneType;                   // salon/one_on_one/library_encounter/sim_xxx

    @Column(name = "related_contact_id")
    private Long relatedContactId;              // one_on_one->contact.id

    @Column(name = "participant_contact_ids", columnDefinition = "JSON")
    private String participantContactIds;       // 沙龙在场书友id列表(JSON)

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;            // 交流开始

    @Column(name = "ended_at")
    private LocalDateTime endedAt;              // 交流结束

    @Column(name = "duration_seconds")
    private Integer durationSeconds;            // 时长

    @Column(name = "participant_count")
    private Integer participantCount;           // 参与人数

    @Column(name = "process_status", nullable = false)
    private Integer processStatus = 0;          // 0待同步 1完成 2同步失败

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

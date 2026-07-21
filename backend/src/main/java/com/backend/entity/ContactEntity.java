package com.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 书友档案 — 关系图谱 / 预警 / 通讯录 / 维护建议 的数据源
 */
@Entity
@Table(name = "contact")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;                        // 档案拥有者->user.id

    @Column(nullable = false)
    private String name;                        // 显示名/备注

    @Column(name = "avatar_url")
    private String avatarUrl;                   // 头像url

    @Column(name = "linked_user_id")
    private Long linkedUserId;                  // 对方若是本系统用户->user.id,预留,第一版全NULL

    @Column(nullable = false)
    private String category = "other";          // 关系分类=图谱扇形=书友圈

    @Column(name = "relation_type", nullable = false)
    private String relationType = "other";      // 社交身份:家人/朋友/同事/同学

    @Column(name = "source_scene")
    private String sourceScene;                 // 认识来源

    private String personality;                 // 性格描述

    @Column(columnDefinition = "JSON")
    private String interests;                   // 兴趣爱好数组(JSON)

    @Column(columnDefinition = "JSON")
    private String labels;                      // 关系/身份标签数组(JSON)

    @Column(name = "intimacy_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal intimacyScore = BigDecimal.ZERO;  // 亲密度连续分0-100

    @Column(name = "last_contact_time")
    private LocalDateTime lastContactTime;      // 上次交流时间,衰减用

    @Column(name = "warning_dismissed_at")
    private LocalDateTime warningDismissedAt;   // 暂不提醒时间,冷却期内跳过

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;            // 软删

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

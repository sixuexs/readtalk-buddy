-- =============================================================================
-- 阅谈智伴 · 评价体系数据模型 DDL
-- 库划分：MySQL — 需聚合 / 排序 / 关联 / 强 schema 的数据
-- 字符集：utf8mb4 / utf8mb4_unicode_ci / InnoDB
-- 不建物理外键，应用层保证完整性（见 §0.3 设计文档）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. evaluation_record — 社交能力评估记录（中枢表）
-- 定位：雷达图 / 折线图 / 亲密度"深度·质量"分（仅 1 对 1）的数据源
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS evaluation_record (
  id                      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id                 BIGINT UNSIGNED NOT NULL                COMMENT '被评估用户->user.id',
  source                  TINYINT         NOT NULL                COMMENT '1=real 2=simulation',
  scene_type              VARCHAR(32)     NOT NULL                COMMENT 'salon/one_on_one/library_encounter/sim_xxx',
  score_clarity           TINYINT UNSIGNED NOT NULL               COMMENT '表达清晰度0-100',
  score_logicality        TINYINT UNSIGNED NOT NULL               COMMENT '逻辑思辨力0-100',
  score_empathy_listening TINYINT UNSIGNED NOT NULL               COMMENT '共情与倾听0-100',
  score_interactivity     TINYINT UNSIGNED NOT NULL               COMMENT '互动积极性0-100',
  score_relaxation        TINYINT UNSIGNED NOT NULL               COMMENT '情绪松弛度0-100',
  score_overall           TINYINT UNSIGNED NOT NULL               COMMENT 'AI加权总分0-100',
  ai_analysis_text        TEXT            NULL                    COMMENT 'AI整篇文字点评',
  self_relative           JSON            NULL                    COMMENT '每维相对上次 up/flat/down,键名=维度短名',
  self_state              VARCHAR(32)     NULL                    COMMENT '状态标签(词表挂起,见文档§8)',
  self_comment            VARCHAR(500)    NULL                    COMMENT '用户评语',
  related_contact_id      BIGINT UNSIGNED NULL                    COMMENT 'one_on_one填contact.id; salon/simulation为NULL',
  interaction_id          BIGINT UNSIGNED NOT NULL                COMMENT '->interaction_meta.id',
  created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time    (user_id, created_at),
  KEY idx_user_source  (user_id, source),
  KEY idx_user_contact (user_id, related_contact_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='社交能力评估记录:雷达图/折线图/亲密度(1v1深度质量)数据源';

-- ---------------------------------------------------------------------------
-- 2. contact — 书友档案（已修订版）
-- 定位：关系图谱 / 预警 / 通讯录 / 维护建议 的数据源
-- 两维度并存：category（图谱扇区）+ relation_type（社交身份）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS contact (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id              BIGINT UNSIGNED NOT NULL                COMMENT '档案拥有者->user.id',
  name                 VARCHAR(64)     NOT NULL                COMMENT '显示名/备注',
  avatar_url           VARCHAR(500)    NULL                    COMMENT '头像url',
  linked_user_id       BIGINT UNSIGNED NULL                    COMMENT '对方若是本系统用户->user.id,预留,第一版全NULL',
  category             VARCHAR(32)     NOT NULL DEFAULT 'other' COMMENT '关系分类=图谱扇形=书友圈(词表挂起,见§8)',
  relation_type        VARCHAR(32)     NOT NULL DEFAULT 'other' COMMENT '社交身份:家人/朋友/同事/同学(与category正交)',
  source_scene         VARCHAR(128)    NULL                    COMMENT '认识来源',
  personality          VARCHAR(200)    NULL                    COMMENT '性格描述',
  interests            JSON            NULL                    COMMENT '兴趣爱好数组',
  labels               JSON            NULL                    COMMENT '关系/身份标签数组',
  intimacy_score       DECIMAL(5,2)    NOT NULL DEFAULT 0.00   COMMENT '亲密度连续分0-100,物化快照',
  last_contact_time    DATETIME        NULL                    COMMENT '上次交流时间,衰减用',
  warning_dismissed_at DATETIME        NULL                    COMMENT '暂不提醒时间,冷却期内跳过',
  deleted_at           DATETIME        NULL                    COMMENT '软删',
  created_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_linked (user_id, linked_user_id),
  KEY idx_user_list     (user_id, deleted_at, updated_at),
  KEY idx_user_intimacy (user_id, intimacy_score),
  KEY idx_user_last     (user_id, last_contact_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='书友档案:关系图谱/预警/通讯录数据源';

-- ---------------------------------------------------------------------------
-- 3. interaction_meta — 交流记录元数据
-- 定位：亲密度时效 / 频率来源；biz_id 与 MongoDB conversation_detail._id 对应
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS interaction_meta (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  biz_id                VARCHAR(64)     NOT NULL                COMMENT '业务幂等键=Mongo明细_id',
  user_id               BIGINT UNSIGNED NOT NULL                COMMENT '所属用户->user.id',
  type                  TINYINT         NOT NULL                COMMENT '1=real 2=simulation',
  scene_type            VARCHAR(32)     NOT NULL                COMMENT 'salon/one_on_one/library_encounter/sim_xxx',
  related_contact_id    BIGINT UNSIGNED NULL                    COMMENT 'one_on_one->contact.id',
  participant_contact_ids JSON          NULL                    COMMENT '沙龙在场书友id列表',
  started_at            DATETIME        NOT NULL                COMMENT '交流开始',
  ended_at              DATETIME        NULL                    COMMENT '交流结束',
  duration_seconds      INT UNSIGNED    NULL                    COMMENT '时长',
  participant_count     TINYINT UNSIGNED NULL                   COMMENT '参与人数',
  process_status        TINYINT         NOT NULL DEFAULT 0      COMMENT '0待同步1完成2同步失败',
  created_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_biz_id (biz_id),
  KEY idx_user_contact_time (user_id, related_contact_id, started_at),
  KEY idx_user_started      (user_id, started_at),
  KEY idx_user_type         (user_id, type),
  KEY idx_status            (process_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='交流记录元数据:亲密度时效/频率来源';

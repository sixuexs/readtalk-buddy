package com.backend.constant;

/**
 * 沟通辅助（复盘版）常量 —— 场景类型 / 说话人角色 / 来源标记的唯一参数源，仿 IntimacyConstants 纪律。
 *
 * <p>口径对齐《评价体系数据结构设计文档》：
 * scene_type ∈ {salon, one_on_one, library_encounter, sim_xxx}；source 1=real 2=simulation。
 */
public final class AssistConstants {

    private AssistConstants() {}

    // ──── 场景类型（对齐 schema.scene_type 注释） ────
    public static final String SCENE_ONE_ON_ONE = "one_on_one";
    public static final String SCENE_SALON = "salon";

    // ──── evaluation_record.source / interaction_meta.type ────
    /** 真实线下沟通（复盘版来源）。 */
    public static final int SOURCE_REAL = 1;

    // ──── conversation_detail.speaker_role（对齐设计文档 §5） ────
    public static final String ROLE_USER = "user";
    public static final String ROLE_CONTACT = "contact";
    public static final String ROLE_UNKNOWN = "unknown";

    // ──── 前端 speakerMap 的特殊值 ────
    /** 前端标注该说话人"是我"。 */
    public static final String MAP_USER = "user";
    /** 前端标注该说话人为"陌生人/未识别"，不计入评分与亲密度。 */
    public static final String MAP_UNKNOWN = "unknown";

    // ──── conversation.origin（新增字段，区分模拟/线下） ────
    public static final String ORIGIN_OFFLINE = "offline";
    public static final String ORIGIN_SIMULATION = "simulation";

    // ──── 音频硬约束（阿里云同步版：≤5 分钟、Base64 ≤10MB） ────
    /** 单场录音上限（秒）。 */
    public static final int MAX_AUDIO_SECONDS = 300;
    /** Base64 编码前音频字节上限（编码后须 ≤10MB，留余量取 7MB）。 */
    public static final int MAX_AUDIO_BYTES = 7 * 1024 * 1024;
    /** 默认采样率。 */
    public static final int DEFAULT_SAMPLE_RATE = 16000;

    /** 场景类型校验：白名单外按 one_on_one 处理（红线 2：不报错只降级）。 */
    public static boolean isValidScene(String scene) {
        return SCENE_ONE_ON_ONE.equals(scene) || SCENE_SALON.equals(scene);
    }

    /** 场景中文名（写入 conversation.theme 供列表展示）。 */
    public static String sceneCn(String scene) {
        if (SCENE_SALON.equals(scene)) {
            return "线下沙龙";
        }
        return "线下沟通";
    }

    /** 生成 biz_id = interaction_meta.biz_id = conversation_detail._id。 */
    public static String newBizId() {
        return "BZ" + System.currentTimeMillis()
                + String.format("%04d", (int) (Math.random() * 10000));
    }
}

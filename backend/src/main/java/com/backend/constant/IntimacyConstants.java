package com.backend.constant;

/**
 * 亲密度计算常量 —— E 模块公式的统一参数源。
 *
 * 四分量公式（全面等权，∑ = 1.0）：
 *   intimacy = round(时效×0.25 + 频率×0.25 + 深度×0.25 + 质量×0.25)
 *
 * 当前实现态：P1(无关联字段) + P3(interaction_meta 无写入) →
 *   频率=0、深度=0、质量=0 →
 *   第一版 intimacy ≈ round(时效×0.25)
 */
public final class IntimacyConstants {

    private IntimacyConstants() { /* constants only */ }

    // ── 四分量权重（全面等权，∑ = 1.0）──
    public static final double W_TTL     = 0.25;  // 时效（lastContactDays）
    public static final double W_FREQ    = 0.25;  // 频率（interaction_meta count30d；P3=空 → 降级=0）
    public static final double W_DEPTH   = 0.25;  // 深度（avg msg/conv；P1=无关联 → 降级=空）
    public static final double W_QUALITY = 0.25;  // 质量（avg 5D score；P1=无关联 → 降级=空）

    // ── 亲密度环阈值 ──
    /** 核心区（内环）*/
    public static final int CORE_THRESHOLD = 70;
    /** 中间区（中环）*/
    public static final int MID_THRESHOLD  = 40;
    // <40 = 边缘区（外环）

    // ── 时效(TTL) 衰减参数 ──
    public static final double TTL_HALFLIFE_DAYS = 30.0;   // 半衰期（天）
    public static final double TTL_FLOOR          = 0.10;   // 最低衰减系数（10%）

    // ── 频率(FREQ) 归一化参数 ──
    /** 频率观察窗口（天）：只统计该窗口内的互动次数 */
    public static final int FREQ_WINDOW_DAYS = 30;
    /** 互动次数达到此值即视为"高频"，归一化为 1.0 */
    public static final int FREQ_FULL_COUNT  = 5;

    // ── 深度(DEPTH) 归一化参数 ──
    /** 平均消息数达到此值即视为"深度饱满"，归一化为 1.0 */
    public static final int DEPTH_FULL_MESSAGES = 20;

    // ── 降级默认值（对应 P1/P3/P6 空数据通道）──
    public static final double DEGRADE_FREQ_SCORE  = 0.0;  // 频率分量降级
    public static final double DEGRADE_DEPTH_SCORE  = 0.0; // 深度分量降级
    public static final double DEGRADE_QUALITY_SCORE = 0.0; // 质量分量降级

    /** 亲密度最低分 */
    public static final int FLOOR = 0;

    // ── 兜底总分 ──
    /** 当所有分量均降级时，兜底亲密度 = round(TTL_FLOOR × 100 × W_TTL) ≈ 2-3 */
    public static final int FALLBACK_INTIMACY = (int) Math.round(TTL_FLOOR * 100);
}

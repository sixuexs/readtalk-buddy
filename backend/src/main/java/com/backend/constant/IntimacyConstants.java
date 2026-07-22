package com.backend.constant;

/**
 * 亲密度计算常量 —— E 模块公式的统一参数源。
 *
 * 四分量权重（全加起来 = 100%）：
 *   intimacy = round(时效×W1 + 频率×W2 + 深度×W3 + 质量×W4)
 *
 * 当前实现态：P1(无关联字段) + P3(interaction_meta 无写入) →
 *   时效=降级(仅 lastContactDays)、频率=0、深度=空、质量=空 →
 *   第一版 intimacy ≈ round(时效×W_TTL + 0×W2 + 0×W3 + 0×W4)
 */
public final class IntimacyConstants {

    private IntimacyConstants() { /* constants only */ }

    // ── 四分量权重（∑ = 1.0）──
    public static final double W_TTL     = 0.35;  // 时效（lastContactDays）
    public static final double W_FREQ    = 0.25;  // 频率（interaction_meta count30d；P3=空 → 降级=0）
    public static final double W_DEPTH   = 0.20;  // 深度（avg msg/conv；P1=无关联 → 降级=空）
    public static final double W_QUALITY = 0.20;  // 质量（avg 5D score；P1=无关联 → 降级=空）

    // ── 亲密度环阈值 ──
    /** 核心区（内环）*/
    public static final int CORE_THRESHOLD = 70;
    /** 中间区（中环）*/
    public static final int MID_THRESHOLD  = 40;
    // <40 = 边缘区（外环）

    // ── 时效(TTL) 衰减参数 ──
    public static final double TTL_HALFLIFE_DAYS = 30.0;   // 半衰期（天）
    public static final double TTL_FLOOR          = 0.10;   // 最低衰减系数（10%）

    // ── 降级默认值（对应 P1/P3/P6 空数据通道）──
    public static final double DEGRADE_FREQ_SCORE  = 0.0;  // 频率分量降级
    public static final double DEGRADE_DEPTH_SCORE  = 0.0; // 深度分量降级
    public static final double DEGRADE_QUALITY_SCORE = 0.0; // 质量分量降级

    // ── 兜底总分 ──
    /** 当所有分量均降级时，兜底亲密度 = floor(W_TTL) * 100 ≈ 10 */
    public static final int FALLBACK_INTIMACY = (int) Math.round(TTL_FLOOR * 100);
}

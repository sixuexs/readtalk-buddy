package com.backend.constant;

/**
 * 关系预警规则常量 —— C3 预警引擎的统一阈值源。
 */
public final class WarningConstants {

    private WarningConstants() { /* constants only */ }

    // ── 亲密度阈值 ──
    /** 亲密度低于此值触发预警 */
    public static final int DRIFT_INTIMACY_THRESHOLD = 40;
    /** 严重疏远阈值 */
    public static final int SEVERE_DRIFT_THRESHOLD = 20;

    // ── 联系天数阈值 ──
    /** 超过此天数未联系触发预警 */
    public static final int DRIFT_DAYS_THRESHOLD = 30;
    /** 长期未联系提醒阈值 */
    public static final int REMINDER_DAYS_THRESHOLD = 14;

    // ── 质量预警（P1=无关联 → 降级）──
    /** 质量分持续下降低于此值时触发质量预警 */
    public static final double QUALITY_DECLINE_THRESHOLD = 0.4;
    /** 质量预警观察窗口（会话数）*/
    public static final int QUALITY_WINDOW_SIZE = 5;

    // ── 抑制／挽救 ──
    /** 用户拒绝挽救后，预警抑制天数 */
    public static final int SUPPRESS_DAYS = 30;
}

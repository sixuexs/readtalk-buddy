/**
 * 预警等级共享样式常量 —— 图谱节点角标、通讯录角标、溢出按钮圆点、成员浮层统一消费。
 *
 * 配色说明：RED（疏远预警）用深红、ORANGE（沉寂预警）用琥珀橙，
 * 拉开色相与明度差，避免旧配色 #EF4444/#F97316 肉眼难分的问题。
 * YELLOW 保留占位（当前后端仅产出 RED/ORANGE 两级）。
 */
import type { WarningLevel, WarningType } from '@/types/relationGraph'

export const LEVEL_COLORS: Record<WarningLevel, string> = {
  RED: '#DC2626',
  ORANGE: '#F59E0B',
  YELLOW: '#FBBF24',
}

/** 预警类型中文名（浮层列表与图例展示用；与 WarningCard 的"滑落预警/沉寂预警"口径一致） */
export const WARN_TYPE_LABELS: Record<WarningType, string> = {
  DECAY: '滑落',
  STAGNATION: '沉寂',
}

/** 等级排序权重：取一组预警中的最高级 */
const LEVEL_RANK: Record<WarningLevel, number> = { RED: 3, ORANGE: 2, YELLOW: 1 }

export function levelRank(level: WarningLevel): number {
  return LEVEL_RANK[level] ?? 0
}

export { LEVEL_RANK }

import type { RelationType } from '@/types/relation'

/**
 * 鲜亮活泼配色 — 糖果 pastel + 高饱和点缀
 * 家人暖橙、好友天蓝、同事薰紫、同窗薄荷，整体偏暖亮
 */
export const WEB = {
  pageBg: '#FFF6FA',
  pageBgGradient:
    'linear-gradient(165deg, #FFF0F8 0%, #F0F6FF 45%, #F2FFF8 100%)',
  card: '#FFFFFF',
  cardMuted: '#FFFBFE',
  border: '#F0E4F0',
  borderLight: '#F5F0FA',

  text: '#2A3441',
  textSecondary: '#5C6B7A',
  textMuted: '#8E9DAB',

  primary: '#3B9EFF',
  primaryDark: '#1E88E5',
  accent: '#FF7EB3',
  accentDark: '#FF5C9A',
  accentWarm: '#FFAB40',
  accentMint: '#2ECFA0',

  shadow: 'rgba(59, 158, 255, 0.12)',
  shadowSoft: 'rgba(255, 126, 179, 0.08)',
} as const

/** 关系类型 — 四色分明、饱和度高 */
export const RELATION_PALETTE: Record<
  RelationType,
  { main: string; light: string; soft: string; shadow: string; gradient: string }
> = {
  家人: {
    main: '#FF8A5C',
    light: '#FFF0E8',
    soft: '#FFC4A8',
    shadow: 'rgba(255, 138, 92, 0.4)',
    gradient: 'linear-gradient(145deg, #FFB088, #FF7043)',
  },
  朋友: {
    main: '#3B9EFF',
    light: '#E8F4FF',
    soft: '#8CC8FF',
    shadow: 'rgba(59, 158, 255, 0.4)',
    gradient: 'linear-gradient(145deg, #6BB8FF, #2196F3)',
  },
  同事: {
    main: '#A78BFA',
    light: '#F3EEFF',
    soft: '#C4B5FD',
    shadow: 'rgba(167, 139, 250, 0.4)',
    gradient: 'linear-gradient(145deg, #C4B5FD, #8B5CF6)',
  },
  同学: {
    main: '#2ECFA0',
    light: '#E6FBF3',
    soft: '#7EE8C8',
    shadow: 'rgba(46, 207, 160, 0.4)',
    gradient: 'linear-gradient(145deg, #6EE7B7, #10B981)',
  },
}

export const SELF_NODE_STYLE = {
  color: '#FF7EB3',
  border: '#FFFFFF',
  shadow: 'rgba(255, 126, 179, 0.45)',
}

/** 亲密度连线 — 由浅到浓的彩虹感 */
export const INTIMACY_LINE_COLORS = [
  { min: 80, color: '#FF6B9D' },
  { min: 60, color: '#3B9EFF' },
  { min: 40, color: '#FFB347' },
  { min: 0, color: '#E8ECF4' },
] as const

export const INTIMACY_LEGEND = [
  { label: '疏远', color: '#E8ECF4', height: 2 },
  { label: '一般', color: '#FFD699', height: 3 },
  { label: '亲密', color: '#7EC4FF', height: 4 },
  { label: '很近', color: '#FF6B9D', height: 6 },
] as const

export const RELATION_LEGEND: { type: RelationType; label: string; icon: string }[] = [
  { type: '家人', label: '家人星', icon: '🏠' },
  { type: '朋友', label: '好友星', icon: '🌟' },
  { type: '同事', label: '同事星', icon: '💼' },
  { type: '同学', label: '同窗星', icon: '📖' },
]

export function getRelationPalette(type: RelationType) {
  return RELATION_PALETTE[type]
}

/** 关系分析聚焦顶栏 — 浅色 pastel，避免高饱和色块 */
export function insightHeroGradient(type: RelationType): string {
  const map: Record<RelationType, string> = {
    家人: 'linear-gradient(135deg, #FFF5F0 0%, #FFE8DC 100%)',
    朋友: 'linear-gradient(135deg, #F7FBFF 0%, #E8F2FC 100%)',
    同事: 'linear-gradient(135deg, #FAF8FF 0%, #F0EBFF 100%)',
    同学: 'linear-gradient(135deg, #F4FDF9 0%, #E2F8EF 100%)',
  }
  return map[type]
}

export function intimacyToLineColor(
  intimacy: number,
  highlighted = false,
  relationType?: RelationType,
): string {
  if (highlighted && relationType) return RELATION_PALETTE[relationType].main
  if (highlighted) return WEB.primaryDark
  for (const band of INTIMACY_LINE_COLORS) {
    if (intimacy >= band.min) return band.color
  }
  return '#E8ECF4'
}

/** 详情顶栏 — 按关系类型的高饱和渐变 */
export function drawerHeaderGradient(type: RelationType): string {
  const map: Record<RelationType, string> = {
    家人: 'linear-gradient(155deg, #FFB088 0%, #FF7043 100%)',
    朋友: 'linear-gradient(155deg, #6BB8FF 0%, #2196F3 100%)',
    同事: 'linear-gradient(155deg, #C4B5FD 0%, #8B5CF6 100%)',
    同学: 'linear-gradient(155deg, #6EE7B7 0%, #10B981 100%)',
  }
  return map[type]
}

/** 标签 — 鲜亮 pastel 轮换 */
export const TAG_PALETTES = [
  { bg: '#E8F4FF', text: '#1E88E5' },
  { bg: '#FFE8F2', text: '#E91E8C' },
  { bg: '#FFF3E0', text: '#F57C00' },
  { bg: '#E8FBF3', text: '#059669' },
  { bg: '#F3EEFF', text: '#7C3AED' },
] as const

export function tagPaletteByIndex(index: number) {
  return TAG_PALETTES[index % TAG_PALETTES.length]
}

export function intimacyBadgeColor(intimacy: number): string {
  if (intimacy >= 80) return '#FF6B9D'
  if (intimacy >= 60) return '#3B9EFF'
  if (intimacy >= 40) return '#FFB347'
  return '#B0BEC5'
}

export function intimacyBarColor(intimacy: number): string {
  if (intimacy >= 80) return '#FF6B9D'
  if (intimacy >= 60) return '#3B9EFF'
  if (intimacy >= 40) return '#FFB347'
  return '#E8ECF4'
}

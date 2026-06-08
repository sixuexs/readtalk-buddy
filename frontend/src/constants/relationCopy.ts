import type { RelationType } from '@/types/relation'

/** 关系图谱页文案与图标（生动向） */
export const RELATION_PAGE = {
  banner: {
    icon: '🪐',
    title: '你的社交小小宇宙',
    subtitle: '每一颗「星」都值得被看见，点一点认识他们～',
  },
  stats: {
    contacts: { icon: '👥', label: '星友圈' },
    intimacy: { icon: '💫', label: '羁绊温度' },
  },
  viewModes: {
    graph: { icon: '🕸️', label: '星图漫游' },
    list: { icon: '📒', label: '好友手帐' },
  },
  legend: {
    relationTitle: '🎨 他们是我的…',
    intimacyTitle: '💝 亲疏悄悄话',
    hint: '点点星球听故事 · 线越近心越近',
  },
  sidebar: {
    icon: '🔍',
    title: '快速寻星',
    hint: '点名字，星图上就会亮起来',
  },
  sidebarSearch: {
    icon: '🔍',
    title: '快速寻星',
    placeholder: '输入名字，匹配星友关系…',
    hint: '输入名字或关系标签，星图上就会亮起来',
    noResult: '没有找到匹配的星友，换个关键词试试～',
  },
  listHeader: {
    icon: '📒',
    title: '好友手帐',
    hint: '按羁绊深浅排序，别让在乎的人走远',
  },
} as const

/** 关系类型小图标 */
export const RELATION_TYPE_ICON: Record<RelationType, string> = {
  家人: '🏠',
  朋友: '🌟',
  同事: '💼',
  同学: '📖',
}

/** 详情弹层区块标题 */
export const DRAWER_SECTIONS = {
  intimacy: '💞 我们的亲密度',
  personality: '🎭 Ta 的性格速写',
  interests: '🎯 Ta 的快乐星球',
  labels: '🏷️ 身份小标签',
} as const

/** 亲密度图例趣味文案 */
export const INTIMACY_LEGEND_FUN: Record<string, string> = {
  疏远: '渐行渐远',
  一般: '礼貌距离',
  亲密: '热络常聊',
  很近: '铁汁儿级',
}

export function relationTypeLabel(type: RelationType): string {
  const map: Record<RelationType, string> = {
    家人: '家人星',
    朋友: '好友星',
    同事: '同事星',
    同学: '同窗星',
  }
  return map[type]
}

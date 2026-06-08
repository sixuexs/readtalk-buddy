import type { RelationContact } from '@/types/relation'
import type { RelationWarningItem } from '@/types/relationInsight'

const LOW_INTIMACY_THRESHOLD = 50
const WARN_INTIMACY = 45
const WARN_DAYS = 30

/** 聚焦联系人：优先选中项，否则取亲密度最低 */
export function resolveFocusContact(
  contacts: RelationContact[],
  selectedId?: string | null,
): RelationContact | null {
  if (!contacts.length) return null
  if (selectedId) {
    const picked = contacts.find((c) => c.id === selectedId)
    if (picked) return picked
  }
  return [...contacts].sort((a, b) => a.intimacy - b.intimacy)[0]
}

/** 是否需要展示智能提问条 */
export function shouldShowQueryBar(contact: RelationContact | null): boolean {
  return !!contact && contact.intimacy < LOW_INTIMACY_THRESHOLD
}

export function buildQueryText(contact: RelationContact): string {
  return `「${contact.name}」亲密度偏低，该如何主动维护？`
}

export function focusLabels(contact: RelationContact): string[] {
  return contact.labels?.length ? contact.labels : [contact.relationType]
}

function interestText(contact: RelationContact): string {
  if (contact.interests?.length) return contact.interests.join('、')
  return '日常近况'
}

function labelText(contact: RelationContact): string {
  return focusLabels(contact).map((l) => `「${l}」`).join('、')
}

export interface SuggestTipItem {
  icon: string
  text: string
  tone: 'blue' | 'purple' | 'warm' | 'mint'
}

export function buildSuggestTips(contact: RelationContact): SuggestTipItem[] {
  const topics = interestText(contact)
  const items: SuggestTipItem[] = [
    {
      icon: '💬',
      text: `围绕「${topics}」发起一次轻松闲聊，自然拉近距离。`,
      tone: 'blue',
    },
    {
      icon: '🤝',
      text: `结合${contact.relationType}身份，在合适节点送上问候或近况分享。`,
      tone: 'purple',
    },
  ]
  if (contact.lastContactDays >= 14) {
    items.push({
      icon: '⏰',
      text: `已 ${contact.lastContactDays} 天未联系，可先发一条简短消息破冰。`,
      tone: 'warm',
    })
  }
  if (contact.interests?.length) {
    items.push({
      icon: '🎯',
      text: `分享与「${contact.interests[0]}」相关的资讯或活动邀请，更容易获得回应。`,
      tone: 'mint',
    })
  }
  return items
}

export function buildAnalysisTip(contact: RelationContact): {
  icon: string
  text: string
  tone: 'warm' | 'mint'
} {
  if (contact.intimacy < LOW_INTIMACY_THRESHOLD) {
    return {
      icon: '🌱',
      text: '仍有提升空间，可通过共同兴趣或节日问候加深联结。',
      tone: 'warm',
    }
  }
  return {
    icon: '✨',
    text: '关系状态良好，保持适度互动即可让联结更稳固。',
    tone: 'mint',
  }
}

/** 关系分析 — 与参考稿一致的段落 */
export function buildAnalysisParagraphs(contact: RelationContact): string[] {
  return [
    `亲密度 ${contact.intimacy}%，标签为${labelText(contact)}。`,
    `性格特征：${contact.personality || '待补充'}；共同话题可关注：${interestText(contact)}。`,
    contact.intimacy < LOW_INTIMACY_THRESHOLD
      ? '仍有提升空间，可通过共同兴趣或节日问候加深联结。'
      : '关系状态良好，保持适度互动即可让联结更稳固。',
  ]
}

/** 维护建议 */
export function buildSuggestItems(contact: RelationContact): string[] {
  const topics = interestText(contact)
  const items = [
    `围绕「${topics}」发起一次轻松闲聊，自然拉近距离。`,
    `结合${contact.relationType}身份，在合适节点送上问候或近况分享。`,
  ]
  if (contact.lastContactDays >= 14) {
    items.push(`已 ${contact.lastContactDays} 天未联系，可先发一条简短消息破冰。`)
  }
  if (contact.interests?.length) {
    items.push(`分享与「${contact.interests[0]}」相关的资讯或活动邀请，更容易获得回应。`)
  }
  return items
}

/** 预警列表 */
export function buildWarnings(contacts: RelationContact[]): RelationWarningItem[] {
  const list: RelationWarningItem[] = []
  for (const c of contacts) {
    if (c.intimacy < WARN_INTIMACY) {
      list.push({
        contactId: c.id,
        name: c.name,
        level: 'high',
        message: `亲密度仅 ${c.intimacy}%，建议优先维护。`,
      })
    } else if (c.lastContactDays > WARN_DAYS) {
      list.push({
        contactId: c.id,
        name: c.name,
        level: 'medium',
        message: `已 ${c.lastContactDays} 天未联系，关系可能逐渐疏远。`,
      })
    }
  }
  return list.sort((a, b) => {
    const order = { high: 0, medium: 1 }
    return order[a.level] - order[b.level]
  })
}

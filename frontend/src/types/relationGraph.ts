// 关系图谱页（graph.vue）类型定义 — 对齐后端 /api/relation/* 响应

/** MySQL contact 行（GET /api/relation/graph 的 contacts 元素） */
export interface GraphContact {
  id: number
  name: string
  relationType: string // 朋友/同事/家人/同学/other
  category: string
  intimacyScore: number
  personality: string | null
  interests: string[]
  labels: string[]
  avatarUrl: string | null
  lastContactTime: string | null
}

export type WarningLevel = 'YELLOW' | 'ORANGE' | 'RED'
export type WarningType = 'STAGNATION' | 'DECAY'

/** 预警条目（GET /api/relation/graph 的 warnings 元素） */
export interface GraphWarning {
  contactId: number
  type: WarningType
  level: WarningLevel
  reason: string
  /** 冷却期内（已"暂不提醒"）：隐藏角标，卡片显示"继续提醒" */
  dismissed: boolean
}

export interface GraphData {
  contacts: GraphContact[]
  warnings: GraphWarning[]
}

export interface GraphRes {
  code: number
  data: GraphData
}

export type AdviceMode = 'light' | 'full'

/** 个性化建议（GET /api/relation/contacts/{id}/advice） */
export interface AdviceResult {
  entryTopics: string[]
  openingLine: string
  cautions: string[]
  recoverSteps: string[]
  expectation: string
}

export interface AdviceRes {
  code: number
  data: AdviceResult
}

export interface DismissRes {
  code: number
  data: { dismissed: boolean }
}

export interface ResumeRes {
  code: number
  data: { resumed: boolean }
}

/** Canvas 命中检测节点 */
export interface GraphNode {
  id: number
  x: number
  y: number
  r: number
  contact: GraphContact
  warning: GraphWarning | null
}

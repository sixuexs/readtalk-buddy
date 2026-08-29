// 消息角色：self 为自己，other 为对方
export type MessageRole = 'self' | 'other'

// 单条消息
export interface Message {
  id: string
  role: MessageRole
  avatar: string
  content: string
  timestamp: number
}

// 场景信息
export interface Scenario {
  scenarioId: string
  title: string
  description: string
}

// POST /api/simulation/send 请求体
export interface SendMessageReq {
  scenarioId: string
  message: string
}

// POST /api/simulation/send 响应体
export interface SendMessageRes {
  code: number
  data: {
    reply: {
      content: string
      timestamp: number
    }
  }
}

// GET /api/simulation/history 响应体
export interface HistoryRes {
  code: number
  data: {
    messages: Message[]
    evaluation?: EvaluationSummary & { score: number }
    selfState?: string
    selfComment?: string
  }
}

// POST /api/simulation/self-review 响应体
export interface SelfReviewRes {
  code: number
  data: {
    saved: boolean
    selfState: string
    selfComment: string
  }
}

// GET /api/simulation/scenario 响应体
export interface ScenarioRes {
  code: number
  data: Scenario
}

// 模拟配置项
export interface SimulationConfig {
  themes: string[]
  personalities: string[]
}

// GET /api/simulation/config 响应体
export interface ConfigRes {
  code: number
  data: SimulationConfig
}

// POST /api/simulation/start 请求体
export interface StartSimulationReq {
  theme: string
  personality: string
  /** 练习对象（书友）的 MongoDB contacts id，可选；为空表示纯能力训练 */
  relatedContactId?: string
}

// POST /api/simulation/start 响应体（含 sessionId 与开场白）
export interface StartSimulationRes {
  code: number
  data: {
    sessionId: string
    greeting: {
      content: string
      timestamp: number
    }
  }
}

// POST /api/simulation/score 响应体
export interface ScoreRes {
  code: number
  data: {
    score: number
    evaluation: EvaluationSummary
  }
}

// 评分详情
export interface EvaluationSummary {
  clarity: number
  logicality: number
  empathyListening: number
  interactivity: number
  relaxation: number
  comment: string
  strengths: string[]
  suggestions: string[]
}

// 会话摘要 —— 用于社交记录列表
export interface SessionSummary {
  sessionId: string
  theme: string
  personality: string
  score: number | null
  messageCount: number
  lastActivity: number
  evaluation: EvaluationSummary | null
}

// GET /api/simulation/sessions 响应体
export interface SessionListRes {
  code: number
  data: SessionSummary[]
}

// ──── 破冰分析 ────

// POST /api/simulation/icebreak 请求体
export interface IceBreakReq {
  myInterests: string[]
  myLabels: string[]
  myMood?: string[]
  otherInterests: string[]
  otherLabels: string[]
  otherPersonality: string
  context: string
}

// 破冰分析结果
export interface IceBreakAnalysis {
  openings: string[]
  topics: string[]
  warnings: string[]
}

// POST /api/simulation/icebreak 响应体
export interface IceBreakRes {
  code: number
  data: {
    contactId: string
    contact: Record<string, unknown>
    analysis: IceBreakAnalysis
  }
}

// 可再生成的建议分区
export type IceBreakSection = 'openings' | 'topics' | 'warnings'

// POST /api/simulation/icebreak/refresh 请求体
export interface IceBreakRefreshReq extends IceBreakReq {
  section: IceBreakSection
}

// POST /api/simulation/icebreak/refresh 响应体
export interface IceBreakRefreshRes {
  code: number
  data: {
    section: string
    items: string[]
  }
}

// ──── 虚拟人物（情景模拟域）────

export interface VirtualCharacter {
  id: string
  name: string
  personality: string
  interests: string[]
  labels: string[]
  description: string
}

export interface VirtualCharacterListRes {
  code: number
  data: VirtualCharacter[]
}

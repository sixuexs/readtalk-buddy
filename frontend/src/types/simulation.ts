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
    /** P2-7/P2-10 靶向训练焦点对比（自由训练场次无此字段） */
    focus?: FocusComparison
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
  /** P2-7 靶向训练：焦点子技能短名（白名单，非法按自由训练） */
  focusSubSkill?: string
  /** P2-7 靶向训练：关联处方任务 id（自动回销用） */
  prescriptionTaskId?: string
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
    focusSubSkill?: string | null
    focusBaseline?: number | null
  }
}

// POST /api/simulation/score 响应体
export interface ScoreRes {
  code: number
  data: {
    score: number
    evaluation: EvaluationSummary
    /** P2-7 靶向训练焦点对比（自由训练场次无此字段） */
    focus?: FocusComparison
  }
}

/** 焦点对比：baseline/current 可为 null（无基线或本场无该子技能观察机会） */
export interface FocusComparison {
  subSkill: string
  cnName: string
  baseline: number | null
  current: number | null
  delta: number | null
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
  /** P1 子技能分：短名→0-100，null=本场无观察机会（词典见 constants/subSkills.ts） */
  subScores?: Record<string, number | null>
  /** P1 证据链：0~4 条原话证据 */
  evidence?: EvidenceItem[]
}

/** P1-6 子技能证据项（对齐后端 ConversationDocument.EvidenceItem；后三字段由 /api/user/evidence 附加） */
export interface EvidenceItem {
  subSkill: string
  messageOrder: number
  quote: string
  issue: string
  rewrite: string
  polarity: 'negative' | 'positive'
  sessionId?: string
  theme?: string
  scoredAt?: string
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
  /** 对方已是已有联系人时传其 id，后端复用不重复建档；虚拟人物/扫码/无 时不传 */
  contactId?: string
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

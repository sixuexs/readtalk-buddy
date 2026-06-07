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

// 会话摘要 —— 用于社交记录列表
export interface SessionSummary {
  sessionId: string
  theme: string
  personality: string
  score: number | null
  messageCount: number
  lastActivity: number
}

// GET /api/simulation/sessions 响应体
export interface SessionListRes {
  code: number
  data: SessionSummary[]
}

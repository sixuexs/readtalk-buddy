import type {
  SendMessageReq,
  SendMessageRes,
  HistoryRes,
  ScenarioRes,
  ConfigRes,
  StartSimulationReq,
  StartSimulationRes,
  SessionListRes,
  ScoreRes,
} from '@/types/simulation'

const BASE_URL = 'http://localhost:8080'

// 获取可选的模拟主题与性格配置
export function getConfig(): Promise<ConfigRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/config`,
      method: 'GET',
      success: (res) => resolve(res.data as ConfigRes),
      fail: reject,
    })
  })
}

// 开始模拟：提交主题与性格，获取 AI 开场白 + sessionId
export function startSimulation(body: StartSimulationReq): Promise<StartSimulationRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/start`,
      method: 'POST',
      data: body,
      success: (res) => resolve(res.data as StartSimulationRes),
      fail: reject,
    })
  })
}

// 发送消息，获取 AI 回复
export function sendMessage(body: SendMessageReq): Promise<SendMessageRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/send`,
      method: 'POST',
      data: body,
      success: (res) => resolve(res.data as SendMessageRes),
      fail: reject,
    })
  })
}

// 获取会话历史消息列表
export function getHistory(sessionId: string): Promise<HistoryRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/history`,
      method: 'GET',
      data: { sessionId },
      success: (res) => resolve(res.data as HistoryRes),
      fail: reject,
    })
  })
}

// 获取场景基本信息
export function getScenario(scenarioId: string): Promise<ScenarioRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/scenario`,
      method: 'GET',
      data: { scenarioId },
      success: (res) => resolve(res.data as ScenarioRes),
      fail: reject,
    })
  })
}

// AI 评分
export function scoreConversation(sessionId: string): Promise<ScoreRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/score?sessionId=${sessionId}`,
      method: 'POST',
      success: (res) => resolve(res.data as ScoreRes),
      fail: reject,
    })
  })
}

// 获取所有会话摘要列表
export function getSessions(): Promise<SessionListRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/sessions`,
      method: 'GET',
      success: (res) => resolve(res.data as SessionListRes),
      fail: reject,
    })
  })
}

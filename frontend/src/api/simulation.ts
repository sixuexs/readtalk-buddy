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
  IceBreakReq,
  IceBreakRes,
  VirtualCharacter,
  VirtualCharacterListRes,
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

// 保存会后复盘自评（状态标签 + 评语）
export function saveSelfReview(
  sessionId: string,
  selfState: string,
  selfComment: string,
): Promise<import('@/types/simulation').SelfReviewRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/self-review?sessionId=${sessionId}`,
      method: 'POST',
      data: { selfState, selfComment },
      success: (res) =>
        resolve(res.data as import('@/types/simulation').SelfReviewRes),
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

// 破冰分析
export function icebreakAnalysis(body: IceBreakReq): Promise<IceBreakRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/icebreak`,
      method: 'POST',
      data: body,
      success: (res) => resolve(res.data as IceBreakRes),
      fail: reject,
    })
  })
}

// ──── 虚拟人物管理 ────

// 获取虚拟人物列表
export function getVirtualCharacters(): Promise<VirtualCharacterListRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/virtual-characters`,
      method: 'GET',
      success: (res) => resolve(res.data as VirtualCharacterListRes),
      fail: reject,
    })
  })
}

// 新增虚拟人物
export function createVirtualCharacter(body: {
  name: string
  personality: string
  interests: string[]
  labels: string[]
  description?: string
}): Promise<{ code: number; data: VirtualCharacter }> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/virtual-characters`,
      method: 'POST',
      data: body,
      success: (res) => resolve(res.data as { code: number; data: VirtualCharacter }),
      fail: reject,
    })
  })
}

// 删除虚拟人物
export function deleteVirtualCharacter(id: string): Promise<{ code: number; data: { deleted: boolean } }> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/virtual-characters/${id}`,
      method: 'DELETE',
      success: (res) => resolve(res.data as { code: number; data: { deleted: boolean } }),
      fail: reject,
    })
  })
}

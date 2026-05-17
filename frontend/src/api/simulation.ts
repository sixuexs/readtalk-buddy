import type {
  SendMessageReq,
  SendMessageRes,
  HistoryRes,
  ScenarioRes,
  ConfigRes,
  StartSimulationReq,
  StartSimulationRes,
} from '@/types/simulation'

// 获取可选的模拟主题与性格配置
// TODO: 替换为 uni.request({ url: '.../api/simulation/config', method: 'GET' })
export function getConfig(): Promise<ConfigRes> {
  return Promise.resolve({
    code: 0,
    data: {
      themes: ['初次见面', '读书交流', '读后感分享', '面试演练', '日常闲聊'],
      personalities: ['乐观开朗自来熟', '不善交际慢热', '幽默风趣社牛', '沉稳内敛观察者'],
    },
  })
}

// 开始模拟：提交用户选择的主题与性格，获取对方开场白
// TODO: 替换为 uni.request({ url: '.../api/simulation/start', method: 'POST', data: body })
export function startSimulation(body: StartSimulationReq): Promise<StartSimulationRes> {
  return new Promise((resolve) => {
    setTimeout(() => {
      // 根据主题和性格 mock 不同的开场白
      const greetings: Record<string, string> = {
        '初次见面': '你好！很高兴认识你，我是小明。你是做什么工作的呀？',
        '读书交流': '嘿，最近在看什么好书？我刚读完一本关于心理学的书，特别有意思！',
        '读后感分享': '刚看完《活着》，感触特别深。你也看过这本书吗？',
        '面试演练': '你好，请坐。可以先简单做个自我介绍吗？',
        '日常闲聊': '哈喽！今天天气真不错，适合出去走走。你喜欢什么样的休闲方式？',
      }
      resolve({
        code: 0,
        data: {
          greeting: {
            content: greetings[body.theme] || '你好！很高兴认识你。',
            timestamp: Date.now(),
          },
        },
      })
    }, 500)
  })
}

// 发送消息到后端，获取对方回复
// TODO: 替换为真实 API 调用 —— uni.request({ url: 'https://api.xxx.com/api/simulation/send', method: 'POST', data: body })
export function sendMessage(body: SendMessageReq): Promise<SendMessageRes> {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        code: 0,
        data: {
          reply: {
            content: '感谢你的问候！很高兴认识你。',
            timestamp: Date.now(),
          },
        },
      })
    }, 500)
  })
}

// 获取当前场景的历史消息列表
// TODO: 替换为真实 API 调用 —— uni.request({ url: 'https://api.xxx.com/api/simulation/history', method: 'GET', data: { scenarioId } })
export function getHistory(scenarioId: string): Promise<HistoryRes> {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        code: 0,
        data: {
          messages: [
            {
              id: '1',
              role: 'other',
              avatar: '/static/other-avatar.png',
              content: '你好！很高兴认识你，我是小明。',
              timestamp: Date.now() - 60000,
            },
          ],
        },
      })
    }, 300)
  })
}

// 获取场景基本信息
// TODO: 替换为真实 API 调用 —— uni.request({ url: 'https://api.xxx.com/api/simulation/scenario', method: 'GET', data: { scenarioId } })
export function getScenario(scenarioId: string): Promise<ScenarioRes> {
  return Promise.resolve({
    code: 0,
    data: {
      scenarioId,
      title: '初次见面',
      description: '模拟第一次与他人见面的社交场景，练习主动介绍自己与回应他人。',
    },
  })
}

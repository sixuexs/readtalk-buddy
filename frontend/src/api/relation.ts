import type { GraphRes, AdviceRes, AdviceMode, DismissRes, ResumeRes } from '@/types/relationGraph'

const BASE_URL = 'http://localhost:8080'

// 获取关系图谱数据（contacts + warnings）
export function getRelationGraph(): Promise<GraphRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/relation/graph`,
      method: 'GET',
      success: (res) => resolve(res.data as GraphRes),
      fail: reject,
    })
  })
}

// 获取联系人个性化建议（light=预警短建议 / full=完整挽救方案）
export function getContactAdvice(contactId: number, mode: AdviceMode): Promise<AdviceRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/relation/contacts/${contactId}/advice?mode=${mode}`,
      method: 'GET',
      success: (res) => resolve(res.data as AdviceRes),
      fail: reject,
    })
  })
}

// 暂不提醒（预警冷却 7 天）
export function dismissWarning(contactId: number): Promise<DismissRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/relation/contacts/${contactId}/dismiss`,
      method: 'POST',
      success: (res) => resolve(res.data as DismissRes),
      fail: reject,
    })
  })
}

// 继续提醒（取消冷却，预警恢复）
export function resumeWarning(contactId: number): Promise<ResumeRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/relation/contacts/${contactId}/resume`,
      method: 'POST',
      success: (res) => resolve(res.data as ResumeRes),
      fail: reject,
    })
  })
}

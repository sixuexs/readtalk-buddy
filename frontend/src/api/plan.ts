import type {
  PlanCurrentRes,
  RegenerateRes,
  SubmitTaskRes,
  RecommendationRes,
  ReportsRes,
  TrendRes,
  AdoptRes,
  DraftRefreshRes,
} from '@/types/plan'

import { BASE_URL } from '@/config'

/** 首屏聚合（先懒周切换；无计划且样本足够时后端同步生成） */
export function getPlanCurrent(): Promise<PlanCurrentRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/plan/current`,
      method: 'GET',
      success: (res) => resolve(res.data as PlanCurrentRes),
      fail: reject,
    })
  })
}

/** 手动重新生成处方（旧计划归档） */
export function regeneratePlan(): Promise<RegenerateRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/plan/regenerate`,
      method: 'POST',
      success: (res) => resolve(res.data as RegenerateRes),
      fail: reject,
    })
  })
}

/** evidence 类任务提交打卡 + AI 点评 */
export function submitPlanTask(taskId: string, text: string): Promise<SubmitTaskRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/plan/tasks/${taskId}/submit`,
      method: 'POST',
      data: { text },
      success: (res) => resolve(res.data as SubmitTaskRes),
      fail: reject,
    })
  })
}

/** 今日推荐训练（subSkill 可选：任务卡按自身焦点取映射行） */
export function getPlanRecommendation(subSkill?: string): Promise<RecommendationRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/plan/recommendation`,
      method: 'GET',
      data: subSkill ? { subSkill } : undefined,
      success: (res) => resolve(res.data as RecommendationRes),
      fail: reject,
    })
  })
}

/** 周报历史（时间倒序） */
export function getReports(limit = 8): Promise<ReportsRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/plan/reports`,
      method: 'GET',
      data: { limit },
      success: (res) => resolve(res.data as ReportsRes),
      fail: reject,
    })
  })
}

/** 采纳下周草稿 */
export function adoptReport(weekKey: string): Promise<AdoptRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/plan/reports/${weekKey}/adopt`,
      method: 'POST',
      success: (res) => resolve(res.data as AdoptRes),
      fail: reject,
    })
  })
}

/** 重新生成下周草稿 */
export function refreshReportDraft(weekKey: string): Promise<DraftRefreshRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/plan/reports/${weekKey}/draft-refresh`,
      method: 'POST',
      success: (res) => resolve(res.data as DraftRefreshRes),
      fail: reject,
    })
  })
}

/** 子技能趋势序列 */
export function getSubskillTrend(subSkill: string, limit = 10): Promise<TrendRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/plan/subskill-trend`,
      method: 'GET',
      data: { subSkill, limit },
      success: (res) => resolve(res.data as TrendRes),
      fail: reject,
    })
  })
}

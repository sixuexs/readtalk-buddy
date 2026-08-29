import { reactive } from 'vue'
import type { IceBreakAnalysis, IceBreakReq } from '@/types/simulation'

/**
 * 破冰分析结果跨页缓存。
 * icebreak.vue（生成页）→ icebreak-result.vue（结果页）通过该模块级 reactive 传递。
 * lastReq 保存生成时的请求参数，供结果页单类建议刷新时复用同样的上下文。
 */
export const icebreakResultStore = reactive<{
  analysis: IceBreakAnalysis | null
  lastReq: IceBreakReq | null
}>({
  analysis: null,
  lastReq: null,
})

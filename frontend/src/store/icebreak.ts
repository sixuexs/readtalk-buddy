import { reactive } from 'vue'
import type { IceBreakAnalysis } from '@/types/simulation'

/**
 * 破冰分析结果跨页缓存。
 * icebreak.vue（生成页）→ icebreak-result.vue（结果页）通过该模块级 reactive 传递。
 */
export const icebreakResultStore = reactive<{
  analysis: IceBreakAnalysis | null
}>({
  analysis: null,
})

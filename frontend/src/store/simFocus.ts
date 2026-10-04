import { reactive } from 'vue'

/**
 * 靶向训练直启参数跨页传递（P2-8）。
 * 提升页（navigateTo 进入）→ 情景模拟是 tabBar 页（switchTab 不能带 query），
 * 故经模块级 reactive store 传参，仿 store/icebreak.ts 模式。
 */
export interface SimFocus {
  focusSubSkill: string
  cnName: string
  theme: string
  personality: string
  prescriptionTaskId: string | null
}

export const simFocusStore = reactive<{ focus: SimFocus | null }>({
  focus: null,
})

/** 写入直启参数（推荐卡/任务卡"去训练"按钮调用） */
export function setSimFocus(focus: SimFocus): void {
  simFocusStore.focus = { ...focus }
}

/** 读取并清空（simulation.vue onShow 消费一次） */
export function takeSimFocus(): SimFocus | null {
  const cur = simFocusStore.focus
  simFocusStore.focus = null
  return cur
}

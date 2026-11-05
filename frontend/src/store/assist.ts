import { reactive } from 'vue'
import type { AsrSentence, AssistSceneType, SpeakerSummary } from '@/types/assist'

/**
 * 录音转写结果跨页缓存：record.vue（录音页）→ confirm.vue（归属确认页）。
 *
 * 转写稿体量较大且含结构，不适合走 navigateTo query，故用模块级 reactive 传递，
 * 仿 store/icebreak.ts、store/simFocus.ts 模式（项目无 Pinia）。
 */
export const assistTranscriptStore = reactive<{
  sentences: AsrSentence[]
  speakers: SpeakerSummary[]
  mock: boolean
  /** 仅录本人：提交时丢弃他人发言（隐私强保护） */
  onlyMe: boolean
  sceneType: AssistSceneType
}>({
  sentences: [],
  speakers: [],
  mock: false,
  onlyMe: false,
  sceneType: 'one_on_one',
})

/** 写入转写结果（录音页转写成功后调用） */
export function setTranscript(payload: {
  sentences: AsrSentence[]
  speakers: SpeakerSummary[]
  mock: boolean
  onlyMe: boolean
  sceneType: AssistSceneType
}): void {
  assistTranscriptStore.sentences = payload.sentences
  assistTranscriptStore.speakers = payload.speakers
  assistTranscriptStore.mock = payload.mock
  assistTranscriptStore.onlyMe = payload.onlyMe
  assistTranscriptStore.sceneType = payload.sceneType
}

/** 读取（确认页 onLoad 消费，不清空以便返回重试） */
export function getTranscript() {
  return assistTranscriptStore
}

/** 清空（复盘提交成功后调用） */
export function clearTranscript(): void {
  assistTranscriptStore.sentences = []
  assistTranscriptStore.speakers = []
  assistTranscriptStore.mock = false
  assistTranscriptStore.onlyMe = false
  assistTranscriptStore.sceneType = 'one_on_one'
}

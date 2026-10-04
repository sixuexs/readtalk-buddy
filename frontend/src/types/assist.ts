import type { EvaluationSummary } from '@/types/simulation'

// 沟通辅助（复盘版）类型定义 — 对齐后端 /api/simulation/assist/*

/** ASR 分句结果（speakerId 可能为 null = 未识别出说话人） */
export interface AsrSentence {
  speakerId: number | null
  text: string
  beginTime: number | null
  endTime: number | null
}

/** 说话人汇总（确认页下拉渲染用） */
export interface SpeakerSummary {
  speakerId: number | null
  sentenceCount: number
  sample: string
}

/** POST /assist/transcribe 响应体 data */
export interface TranscribeData {
  sentences: AsrSentence[]
  speakers: SpeakerSummary[]
  speakerCount: number
  mock: boolean
}

export interface TranscribeRes {
  code: number
  data: TranscribeData
}

/** 场景类型 */
export type AssistSceneType = 'one_on_one' | 'salon'

/**
 * 说话人归属值：
 * - 'user'    是我
 * - 'unknown' 陌生人 / 未识别（不计入评分与亲密度）
 * - 其他      联系人 MongoDB id（某书友）
 */
export type SpeakerAssignment = string

/** POST /assist/review 请求体 */
export interface ReviewReq {
  sceneType: AssistSceneType
  relatedContactId?: string
  title?: string
  sentences: AsrSentence[]
  speakerMap: Record<string, SpeakerAssignment>
}

/** POST /assist/review 响应体 */
export interface ReviewRes {
  code: number
  data: {
    sessionId: string
    bizId: string
    sceneType: string
    score: number
    evaluation: EvaluationSummary
    participantCount: number
  }
}

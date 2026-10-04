/**
 * 子技能词典前端镜像（P1-6）。
 * key 与后端 SubSkillConstants 严格一致（snake_case 冻结，C 文档 §0.2 纪律）；
 * 中文展示名与维度归属转写自设计文档 §2.1，禁止在此文件发明新子技能。
 */

export type DimKey =
  | 'clarity'
  | 'logicality'
  | 'empathy_listening'
  | 'interactivity'
  | 'relaxation'

export interface SubSkillDef {
  key: string
  cnName: string
  dimension: DimKey
}

/** 五维展示名（与 plan.vue / home.vue 既有口径一致） */
export const DIM_LABELS: Record<DimKey, string> = {
  clarity: '清晰度',
  logicality: '逻辑性',
  empathy_listening: '共情倾听',
  interactivity: '互动性',
  relaxation: '松弛感',
}

export const DIM_ORDER: DimKey[] = [
  'clarity',
  'logicality',
  'empathy_listening',
  'interactivity',
  'relaxation',
]

/** 16 项子技能（顺序与后端 ALL 一致） */
export const SUB_SKILLS: readonly SubSkillDef[] = [
  { key: 'filler_control', cnName: '填充词控制', dimension: 'clarity' },
  { key: 'point_first', cnName: '观点先行', dimension: 'clarity' },
  { key: 'concrete_wording', cnName: '用词具体', dimension: 'clarity' },
  { key: 'claim_evidence', cnName: '观点-论据配对', dimension: 'logicality' },
  { key: 'structure_linking', cnName: '结构连接', dimension: 'logicality' },
  { key: 'self_consistency', cnName: '前后自洽', dimension: 'logicality' },
  { key: 'emotion_naming', cnName: '情绪命名', dimension: 'empathy_listening' },
  { key: 'paraphrase', cnName: '复述确认', dimension: 'empathy_listening' },
  { key: 'empathic_followup', cnName: '共情追问', dimension: 'empathy_listening' },
  { key: 'active_questioning', cnName: '主动提问', dimension: 'interactivity' },
  { key: 'topic_driving', cnName: '话题推动', dimension: 'interactivity' },
  { key: 'turn_giving', cnName: '发言权让渡', dimension: 'interactivity' },
  { key: 'silence_handling', cnName: '冷场处理', dimension: 'interactivity' },
  { key: 'less_hedging', cnName: '减少道歉退缩词', dimension: 'relaxation' },
  { key: 'self_disclosure', cnName: '敢于自我表露', dimension: 'relaxation' },
  { key: 'non_defensive', cnName: '被质疑不防御', dimension: 'relaxation' },
]

export function subSkillsByDimension(dimension: DimKey): SubSkillDef[] {
  return SUB_SKILLS.filter((s) => s.dimension === dimension)
}

export function subSkillCn(key: string): string {
  return SUB_SKILLS.find((s) => s.key === key)?.cnName ?? key
}

/** 子技能样本不足判定（MIN_SAMPLES=2，与后端常量同值） */
export const MIN_SAMPLES = 2

/**
 * 训练处方（/api/plan）类型契约（P2-8）。
 * 与设计文档 §3/§7 及后端 TrainingPlanDocument 一一对应，字段冻结。
 */

export interface PlanAutoRule {
  kind: 'session_subskill_min' | 'focus_session_count'
  subSkill: string
  minScore?: number | null
  target?: number | null
}

export interface PlanSubmission {
  text: string
  aiFeedback: string
  accepted: boolean
  at: string
}

export interface EvidenceRef {
  sessionId: string
  subSkill: string
}

export type PlanTaskType = 'simulation' | 'realworld' | 'knowledge'
export type PlanTaskStatus = 'pending' | 'in_progress' | 'done' | 'expired'

export interface PlanTask {
  id: string
  type: PlanTaskType
  subSkill: string
  title: string
  instruction: string
  successCriteria?: string
  verifyMode: 'auto' | 'evidence'
  autoRule?: PlanAutoRule | null
  target: number
  progress: number
  status: PlanTaskStatus
  submissions?: PlanSubmission[]
  whyRefs?: EvidenceRef[]
  dueAt?: string
}

export interface PlanDiagnosis {
  focusSubSkills: string[]
  summary: string
  stats?: Record<string, unknown> | null
  evidenceRefs?: EvidenceRef[]
}

export interface TrainingPlan {
  id?: string
  userId?: number
  weekKey: string
  weekStart?: string
  weekEnd?: string
  status: 'active' | 'archived'
  generatedBy?: 'auto' | 'manual' | 'adopted'
  diagnosis?: PlanDiagnosis
  tasks: PlanTask[]
  generatedAt?: string
}

export interface PlanRecommendation {
  subSkill: string
  cnName: string
  theme: string
  personality: string
  feed: string
  taskId: string | null
  summary?: string
}

export interface BadgeView {
  subSkill: string
  level: number
  achievedAt: string
  lastUpgradedAt?: string
}

export interface PlanStatsView {
  subSkillAverages?: Record<string, number | null>
  subSkillSampleCount?: Record<string, number>
  badges?: BadgeView[]
  streakDays?: number
  bestStreak?: number
  totalSessions?: number
  overallScore?: number
}

export interface PlanCurrent {
  generating: boolean
  insufficientData: boolean
  plan: TrainingPlan | null
  lastReport: unknown | null
  recommendation: PlanRecommendation | null
  stats: PlanStatsView
}

export interface PlanCurrentRes {
  code: number
  data: PlanCurrent
}

export interface RegenerateRes {
  code: number
  data: {
    status: 'ok' | 'generating' | 'insufficient_data' | 'error'
    plan: TrainingPlan | null
  }
}

export interface SubmitTaskRes {
  code: number
  data: {
    accepted: boolean
    aiFeedback: string
    progress: number
    status: PlanTaskStatus
    locked: boolean
    remaining: number
  }
}

export interface RecommendationRes {
  code: number
  data: {
    recommendation: PlanRecommendation | null
  }
}

// ──── P3 周报 / 趋势 ────

export interface WeeklyCompletion {
  tasksTotal: number
  tasksDone: number
  tasksExpired: number
  sessionsScored: number
  focusSessions: number
  submissions: number
  submissionsAccepted: number
}

export interface ReportHighlight {
  sessionId: string
  subSkill: string
  quote: string
}

export interface NextWeekDraft {
  summary: string
  tasks: PlanTask[]
}

export interface WeeklyReport {
  id?: string
  userId?: number
  weekKey: string
  planId?: string
  completion: WeeklyCompletion
  deltas?: Record<string, number> | null
  highlights?: ReportHighlight[]
  aiSummary?: string
  nextWeekDraft?: NextWeekDraft | null
  adopted?: boolean
  createdAt?: string
}

export interface ReportsRes {
  code: number
  data: { items: WeeklyReport[] }
}

export interface TrendPoint {
  sessionId: string
  theme: string
  score: number
  scoredAt: string
}

export interface TrendRes {
  code: number
  data: { subSkill: string; points: TrendPoint[] }
}

export interface AdoptRes {
  code: number
  data: { adopted?: boolean; message?: string }
}

export interface DraftRefreshRes {
  code: number
  data: { status: string }
}

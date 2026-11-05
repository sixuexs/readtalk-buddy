/** 用户档案信息 */
export interface UserProfile {
  userId: number
  displayName: string
  biography: string
  status: string
  avatar: string
  personality: string
  interests: string[]
  labels: string[]
}

/** PUT /api/user/profile 请求体 */
export interface UpdateProfileReq {
  userId: number
  displayName: string
  biography: string
  status: string
  avatar: string
  personality: string
  interests: string[]
  labels: string[]
}

/** GET/PUT /api/user/profile 响应体 */
export interface ProfileRes {
  code: number
  data: UserProfile
}

/** P3 徽章达成记录（对齐后端 BadgeRecord） */
export interface BadgeRecord {
  subSkill: string
  level: number
  achievedAt: string
  lastUpgradedAt?: string
}

/** 评分历史记录（GET /api/user/profile 的 scoreHistory 元素） */
export interface ProfileScoreRecord {
  sessionId: string
  theme: string
  score: number
  clarity: number
  logicality: number
  empathyListening: number
  interactivity: number
  relaxation: number
  scoredAt: string
}

/** 能力画像（提升计划页数据源 = GET /api/user/profile 完整文档） */
export interface AbilityProfile {
  userId: number
  // 五维均分 + 综合
  avgClarity: number
  avgLogicality: number
  avgEmpathyListening: number
  avgInteractivity: number
  avgRelaxation: number
  overallScore: number
  totalSessions: number
  // P1-4 子技能聚合 / 徽章 / streak（旧文档无值时为 undefined）
  subSkillAverages?: Record<string, number | null>
  subSkillSampleCount?: Record<string, number>
  badges?: BadgeRecord[]
  streakDays?: number
  bestStreak?: number
  lastActiveDate?: string
  // AI 评估 + 提升计划
  assessment: string
  topStrengths: string[]
  topWeaknesses: string[]
  improvementPlan: string
  weeklyGoals: string[]
  weeklyGoalsStatus: boolean[]
  weeklyGoalSubmissions?: GoalSubmission[]
  scoreHistory: ProfileScoreRecord[]
}

export interface AbilityProfileRes {
  code: number
  data: AbilityProfile
}

/** P0-3 打卡附言提交项 */
export interface GoalSubmissionInput {
  index: number
  text: string
}

/** P0-3 打卡附言记录 */
export interface GoalSubmission {
  index: number
  text: string
  at: string
}

/** PUT /api/user/profile/weekly-goals 请求/响应体 */
export interface WeeklyGoalsStatusReq {
  userId: number
  status: boolean[]
  submissions?: GoalSubmissionInput[]
}

export interface WeeklyGoalsStatusRes {
  code: number
  data: {
    saved: boolean
    weeklyGoalsStatus: boolean[]
    weeklyGoalSubmissions?: GoalSubmission[]
    message?: string
  }
}

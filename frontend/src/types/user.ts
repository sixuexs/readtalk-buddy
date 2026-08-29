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
  // AI 评估 + 提升计划
  assessment: string
  topStrengths: string[]
  topWeaknesses: string[]
  improvementPlan: string
  weeklyGoals: string[]
  weeklyGoalsStatus: boolean[]
  scoreHistory: ProfileScoreRecord[]
}

export interface AbilityProfileRes {
  code: number
  data: AbilityProfile
}

/** PUT /api/user/profile/weekly-goals 请求/响应体 */
export interface WeeklyGoalsStatusReq {
  userId: number
  status: boolean[]
}

export interface WeeklyGoalsStatusRes {
  code: number
  data: {
    saved: boolean
    weeklyGoalsStatus: boolean[]
  }
}

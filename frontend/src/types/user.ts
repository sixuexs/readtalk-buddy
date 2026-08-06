/** 用户档案信息 */
export interface UserProfile {
  userId: number
  displayName: string
  biography: string
  status: string
  avatarUrl: string
}

/** PUT /api/user/profile 请求体 */
export interface UpdateProfileReq {
  userId: number
  displayName: string
  biography: string
  status: string
}

/** GET/PUT /api/user/profile 响应体 */
export interface ProfileRes {
  code: number
  data: UserProfile
}

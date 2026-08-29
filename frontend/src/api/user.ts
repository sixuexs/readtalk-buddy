import type {
  UpdateProfileReq,
  ProfileRes,
  AbilityProfileRes,
  WeeklyGoalsStatusReq,
  WeeklyGoalsStatusRes,
} from '@/types/user'

const BASE_URL = 'http://localhost:8080'

/** 获取用户档案 */
export function getUserProfile(userId: number): Promise<ProfileRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/user/profile`,
      method: 'GET',
      data: { userId },
      success: (res) => resolve(res.data as ProfileRes),
      fail: reject,
    })
  })
}

/** 获取能力画像（提升计划页数据源，同一端点的完整文档） */
export function getAbilityProfile(userId: number): Promise<AbilityProfileRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/user/profile`,
      method: 'GET',
      data: { userId },
      success: (res) => resolve(res.data as AbilityProfileRes),
      fail: reject,
    })
  })
}

/** 更新周目标打卡状态 */
export function updateWeeklyGoalsStatus(body: WeeklyGoalsStatusReq): Promise<WeeklyGoalsStatusRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/user/profile/weekly-goals`,
      method: 'PUT',
      data: body,
      success: (res) => resolve(res.data as WeeklyGoalsStatusRes),
      fail: reject,
    })
  })
}

/** 更新用户档案 */
export function updateUserProfile(body: UpdateProfileReq): Promise<ProfileRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/user/profile`,
      method: 'PUT',
      data: body,
      success: (res) => resolve(res.data as ProfileRes),
      fail: reject,
    })
  })
}

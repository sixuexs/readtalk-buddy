import type { UpdateProfileReq, ProfileRes } from '@/types/user'

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

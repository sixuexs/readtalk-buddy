import { BASE_URL } from '@/config'
import type { ReviewReq, ReviewRes, TranscribeRes } from '@/types/assist'

/**
 * 上传录音并转写（multipart/form-data）。
 *
 * 音频以二进制上传，后端在内存中转 Base64 调阿里云 ASR：
 * 前端不落盘、后端不留存（隐私设计）。
 */
export function transcribeAudio(
  filePath: string,
  format = 'mp3',
  sampleRate = 16000,
): Promise<TranscribeRes> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}/api/simulation/assist/transcribe`,
      filePath,
      name: 'audio',
      formData: { format, sampleRate: String(sampleRate) },
      success: (res) => {
        try {
          resolve(JSON.parse(res.data) as TranscribeRes)
        } catch (e) {
          reject(e)
        }
      },
      fail: reject,
    })
  })
}

/** 归属确认后提交复盘：五维评分 + 三库落库 */
export function submitReview(body: ReviewReq): Promise<ReviewRes> {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api/simulation/assist/review`,
      method: 'POST',
      data: body,
      success: (res) => resolve(res.data as ReviewRes),
      fail: reject,
    })
  })
}

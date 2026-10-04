/**
 * 线下沟通录音封装（沟通辅助复盘版）。
 *
 * 硬约束（来自阿里云 qwen-audio-3.1-asr-flash 同步版）：
 * - 单段 ≤5 分钟（超时需切异步 filetrans，本期不做）
 * - 必须用 mp3/aac 压缩格式：16k·单声道 wav 5 分钟 Base64 后约 12.8MB 会超 10MB 上限
 * - 单声道（说话人分离仅支持单声道）
 *
 * RecorderManager 是全局单例，回调只注册一次，通过模块级 current 会话派发，
 * 避免重复注册导致多次触发。
 */

/** 单段录音上限（秒） */
export const MAX_RECORD_SECONDS = 300

export interface RecorderOptions {
  /** 每秒回调已录秒数 */
  onTick?: (seconds: number) => void
  /** 正常结束（含到点自动停）：返回临时文件路径与时长 */
  onStop?: (tempFilePath: string, durationMs: number) => void
  /** 出错或被打断（来电/切后台） */
  onError?: (message: string) => void
}

export interface RecorderHandle {
  /** 主动停止并产出结果 */
  stop: () => void
  /** 取消：丢弃本次录音，不产出结果 */
  cancel: () => void
}

interface Session {
  onTick?: (seconds: number) => void
  onStop?: (tempFilePath: string, durationMs: number) => void
  onError?: (message: string) => void
  timer: ReturnType<typeof setInterval> | null
  seconds: number
  cancelled: boolean
}

let manager: any = null
let current: Session | null = null

function stopTimer(s: Session): void {
  if (s.timer !== null) {
    clearInterval(s.timer)
    s.timer = null
  }
}

/** 惰性初始化单例 RecorderManager，回调只注册一次 */
function ensureManager(): any {
  if (manager) return manager
  manager = uni.getRecorderManager()

  manager.onStop((res: any) => {
    const s = current
    if (!s) return
    stopTimer(s)
    current = null
    if (s.cancelled) return
    s.onStop?.(res?.tempFilePath || '', res?.duration || s.seconds * 1000)
  })

  manager.onError((err: any) => {
    const s = current
    if (s) stopTimer(s)
    current = null
    s?.onError?.(err?.errMsg || '录音失败，请检查麦克风权限')
  })

  // 来电 / 切后台会中断录音
  if (typeof manager.onInterruptionBegin === 'function') {
    manager.onInterruptionBegin(() => {
      const s = current
      if (!s) return
      stopTimer(s)
      current = null
      s.onError?.('录音被系统中断（来电或切到后台），请重新录制')
    })
  }

  return manager
}

/** 开始录音（mp3 / 16kHz / 单声道 / 最长 5 分钟） */
export function startRecording(opts: RecorderOptions = {}): RecorderHandle {
  const mgr = ensureManager()

  // 上一次未清理时先停掉，避免并发
  if (current) {
    try {
      mgr.stop()
    } catch {
      /* ignore */
    }
    stopTimer(current)
    current = null
  }

  const session: Session = {
    onTick: opts.onTick,
    onStop: opts.onStop,
    onError: opts.onError,
    timer: null,
    seconds: 0,
    cancelled: false,
  }
  current = session

  mgr.start({
    duration: MAX_RECORD_SECONDS * 1000,
    sampleRate: 16000,
    numberOfChannels: 1,
    encodeBitRate: 48000,
    format: 'mp3',
  })

  session.timer = setInterval(() => {
    session.seconds += 1
    session.onTick?.(session.seconds)
    // 到点自动停止（同步识别硬上限 5 分钟）
    if (session.seconds >= MAX_RECORD_SECONDS) {
      stopRecording()
    }
  }, 1000)

  return {
    stop: stopRecording,
    cancel: cancelRecording,
  }
}

function stopRecording(): void {
  if (!current) return
  try {
    manager?.stop()
  } catch {
    /* ignore */
  }
}

function cancelRecording(): void {
  if (!current) return
  current.cancelled = true
  try {
    manager?.stop()
  } catch {
    /* ignore */
  }
}

/** 秒 → mm:ss */
export function formatDuration(seconds: number): string {
  const s = Math.max(0, Math.floor(seconds))
  const m = Math.floor(s / 60)
  const r = s % 60
  return `${String(m).padStart(2, '0')}:${String(r).padStart(2, '0')}`
}

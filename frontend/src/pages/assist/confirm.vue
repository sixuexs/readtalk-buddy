<template>
  <view class="page-container">
    <scroll-view v-if="rows.length" class="body-scroll" scroll-y>
      <!-- 示例数据提示 -->
      <view v-if="store.mock" class="mock-banner">
        <text class="mock-text">当前为示例转写结果（未配置阿里云 ASR 密钥），仅用于流程演示</text>
      </view>

      <view class="tip-card">
        <text class="tip-title">请确认「谁是谁」</text>
        <text class="tip-desc">
          AI 已按声音分出说话人，但不知道谁是你。第①步标注归属，第②步按时间顺序核对对话，
          确认后提交生成复盘报告；提交前可逐句删除不想保留的内容。
        </text>
        <text v-if="store.onlyMe" class="tip-privacy">
          已开启「复盘只用我的发言」：他人发言不会提交、不会保存。
        </text>
      </view>

      <!-- ===== ① 归属确认（紧凑行） ===== -->
      <view class="card">
        <text class="card-title">① 每位说话人是谁</text>
        <view v-for="g in groups" :key="g.key" class="assign-block">
          <view class="assign-head">
            <view class="assign-dot" :style="{ backgroundColor: colorOf(g.key) }" />
            <text class="assign-name">{{ speakerLabel(g) }}</text>
            <text class="assign-count">{{ g.count }} 句</text>
            <view class="assign-chips">
              <view
                class="assign-chip"
                :class="{ 'assign-chip--active': assignment[g.key] === 'user' }"
                @tap="assign(g.key, 'user')"
              >
                <text class="assign-chip-text">是我</text>
              </view>
              <view
                v-if="!store.onlyMe"
                class="assign-chip"
                :class="{ 'assign-chip--active': assignment[g.key] === 'unknown' }"
                @tap="assign(g.key, 'unknown')"
              >
                <text class="assign-chip-text">陌生人</text>
              </view>
              <text v-else class="assign-locked">他人不提交</text>
            </view>
          </view>

          <!-- 书友选择（非「仅录本人」模式） -->
          <scroll-view v-if="!store.onlyMe && contacts.length" class="chip-scroll" scroll-x>
            <view class="chip-row">
              <view
                v-for="c in contacts"
                :key="c.id"
                class="chip"
                :class="{ 'chip--active': assignment[g.key] === c.id }"
                @tap="assign(g.key, c.id)"
              >
                <text class="chip-text">{{ c.name }}</text>
              </view>
            </view>
          </scroll-view>

          <text class="speaker-sample">样本：{{ g.sample }}</text>
        </view>
      </view>

      <!-- ===== ② 时间线对话流 ===== -->
      <view class="card">
        <view class="card-head">
          <text class="card-title">② 对话流（按时间顺序）</text>
          <text class="card-sub">共 {{ timeline.length }} 句</text>
        </view>
        <view class="tl-list">
          <view v-for="s in timeline" :key="s.idx" class="tl-row">
            <text class="tl-time">{{ fmtTime(s.beginTime) }}</text>
            <view
              class="tl-badge"
              :style="{ borderColor: colorOf(keyOf(s.speakerId)) }"
            >
              <text class="tl-badge-text" :style="{ color: colorOf(keyOf(s.speakerId)) }">
                {{ badgeOf(s) }}
              </text>
            </view>
            <text class="tl-text">{{ s.text }}</text>
            <view class="tl-del" @tap="removeSentence(s.idx)">
              <text class="tl-del-text">×</text>
            </view>
          </view>
        </view>
      </view>

      <view class="submit-wrap">
        <view
          class="submit-btn"
          :class="{ 'submit-btn--disabled': submitting }"
          @tap="handleSubmit"
        >
          <text class="submit-btn-text">{{ submitting ? 'AI 复盘中…' : '生成复盘报告' }}</text>
        </view>
        <text class="submit-hint">复盘结果会计入社交记录与能力画像</text>
      </view>
    </scroll-view>

    <view v-else class="empty-state">
      <text class="empty-text">转写数据已失效</text>
      <view class="empty-btn" @tap="goRecord">
        <text class="empty-btn-text">重新录音</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { submitReview } from '@/api/assist'
import { getTranscript, clearTranscript } from '@/store/assist'
import { getRelationGraph } from '@/api/relation'
import type { GraphContact } from '@/types/relationGraph'
import type { AsrSentence, SpeakerAssignment } from '@/types/assist'

interface SentenceRow extends AsrSentence {
  /** 在原始数组中的下标（删除用） */
  idx: number
}

interface SpeakerGroup {
  key: string
  speakerId: number | null
  sentences: SentenceRow[]
  count: number
  sample: string
}

/** 说话人配色（按分组顺序循环取色，归属区与时间线共用，便于对应） */
const SPEAKER_COLORS = ['#5b8def', '#43b581', '#f59e0b', '#8b5cf6', '#f43f5e', '#06b6d4']

const store = getTranscript()
const rows = ref<SentenceRow[]>([])
const assignment = ref<Record<string, SpeakerAssignment>>({})
const contacts = ref<GraphContact[]>([])
const relatedContactId = ref('')
const submitting = ref(false)

function keyOf(speakerId: number | null): string {
  return speakerId === null || speakerId === undefined ? 'null' : String(speakerId)
}

onLoad(async (query: any) => {
  rows.value = store.sentences.map((s, i) => ({ ...s, idx: i }))
  relatedContactId.value = query?.contactId || ''
  initAssignment()

  if (!rows.value.length) return
  try {
    const res = await getRelationGraph()
    if (res.code === 0 && res.data?.contacts) {
      contacts.value = res.data.contacts.filter((c) => c.name)
    }
  } catch {
    contacts.value = []
  }
})

/** 默认把发言最多的人标为「我」，其余为陌生人；带书友参数时指给该书友 */
function initAssignment() {
  const counts = new Map<string, number>()
  rows.value.forEach((r) => {
    const k = keyOf(r.speakerId)
    counts.set(k, (counts.get(k) || 0) + 1)
  })

  let topKey = ''
  let top = -1
  counts.forEach((v, k) => {
    if (v > top) {
      top = v
      topKey = k
    }
  })

  const map: Record<string, SpeakerAssignment> = {}
  counts.forEach((_, k) => {
    map[k] = k === topKey ? 'user' : 'unknown'
  })

  // 「仅录本人」：他人一律不提交
  if (store.onlyMe) {
    counts.forEach((_, k) => {
      if (k !== topKey) map[k] = 'unknown'
    })
  } else if (relatedContactId.value) {
    const other = Object.keys(map).find((k) => k !== topKey)
    if (other) map[other] = relatedContactId.value
  }

  assignment.value = map
}

const groups = computed<SpeakerGroup[]>(() => {
  const out: SpeakerGroup[] = []
  const index = new Map<string, number>()
  rows.value.forEach((r) => {
    const k = keyOf(r.speakerId)
    let i = index.get(k)
    if (i === undefined) {
      i = out.length
      index.set(k, i)
      out.push({ key: k, speakerId: r.speakerId, sentences: [], count: 0, sample: '' })
    }
    out[i].sentences.push(r)
    out[i].count += 1
  })
  out.forEach((g) => {
    g.sample = g.sentences.length ? g.sentences[0].text : ''
  })
  return out
})

/** 时间线：全部句子按 beginTime 升序（无时间戳的排最后，同级按原序） */
const timeline = computed<SentenceRow[]>(() =>
  [...rows.value].sort((a, b) => {
    const ta = a.beginTime ?? Number.MAX_SAFE_INTEGER
    const tb = b.beginTime ?? Number.MAX_SAFE_INTEGER
    return ta - tb || a.idx - b.idx
  }),
)

const userKey = computed(
  () => Object.keys(assignment.value).find((k) => assignment.value[k] === 'user') || '',
)

function speakerLabel(g: SpeakerGroup): string {
  const n = g.speakerId === null ? '?' : g.speakerId + 1
  return `说话人 ${n}`
}

/** 说话人颜色：按分组顺序稳定取色 */
function colorOf(key: string): string {
  const i = groups.value.findIndex((g) => g.key === key)
  return SPEAKER_COLORS[(i < 0 ? 0 : i) % SPEAKER_COLORS.length]
}

/** 时间线徽章文案：显示归属结果（我 / 书友名 / 陌生人 / 不提交） */
function badgeOf(s: SentenceRow): string {
  const key = keyOf(s.speakerId)
  const a = assignment.value[key]
  if (a === 'user') return '我'
  if (store.onlyMe) return '不提交'
  if (!a || a === 'unknown') return '陌生人'
  const c = contacts.value.find((x) => x.id === a)
  if (c) return c.name
  const n = s.speakerId === null ? '?' : s.speakerId + 1
  return `说话人 ${n}`
}

/** 毫秒 → mm:ss（相对录音起点） */
function fmtTime(ms: number | null): string {
  if (ms === null || ms === undefined) return '--:--'
  const total = Math.max(0, Math.floor(ms / 1000))
  const m = Math.floor(total / 60)
  const sec = total % 60
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

/** 「我」是唯一的：设为 user 时清掉其他组的 user */
function assign(key: string, value: SpeakerAssignment) {
  const next: Record<string, SpeakerAssignment> = { ...assignment.value }
  if (value === 'user') {
    Object.keys(next).forEach((k) => {
      if (k !== key && next[k] === 'user') next[k] = 'unknown'
    })
  }
  next[key] = value
  assignment.value = next
}

function removeSentence(idx: number) {
  rows.value = rows.value.filter((r) => r.idx !== idx)
}

async function handleSubmit() {
  if (submitting.value) return
  if (!rows.value.length) {
    uni.showToast({ title: '没有可提交的发言', icon: 'none' })
    return
  }
  if (!userKey.value) {
    uni.showToast({ title: '请指定哪一位说话人是你', icon: 'none' })
    return
  }

  const list = store.onlyMe
    ? rows.value.filter((r) => keyOf(r.speakerId) === userKey.value)
    : rows.value

  if (!list.length) {
    uni.showToast({ title: '没有可提交的发言', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    const res = await submitReview({
      sceneType: store.sceneType,
      relatedContactId: relatedContactId.value || undefined,
      sentences: list.map((r) => ({
        speakerId: r.speakerId,
        text: r.text,
        beginTime: r.beginTime,
        endTime: r.endTime,
      })),
      speakerMap: assignment.value,
    })

    if (res.code === 0 && res.data?.sessionId) {
      clearTranscript()
      uni.redirectTo({
        url: `/pages/assist/review?sessionId=${res.data.sessionId}&origin=offline`,
      })
    } else {
      const msg = (res.data as unknown as { message?: string })?.message || '复盘失败，请重试'
      uni.showToast({ title: String(msg).slice(0, 40), icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '网络异常，请重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function goRecord() {
  uni.redirectTo({ url: '/pages/assist/record' })
}
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f0f0f0;
  display: flex;
  flex-direction: column;
}

.body-scroll {
  flex: 1;
  min-height: 0;
  padding: 20rpx 0 60rpx;
}

.mock-banner {
  margin: 0 24rpx 16rpx;
  background: #fff6e5;
  border-radius: 12rpx;
  padding: 16rpx 22rpx;
}
.mock-text {
  font-size: 22rpx;
  color: #b7791f;
  line-height: 1.5;
}

.tip-card {
  margin: 0 24rpx 16rpx;
  background: linear-gradient(135deg, #eef4ff, #f6f0ff);
  border: 2rpx solid #dbe6ff;
  border-radius: 16rpx;
  padding: 24rpx 26rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.tip-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #2b4a8f;
}
.tip-desc {
  font-size: 24rpx;
  color: #555;
  line-height: 1.6;
}
.tip-privacy {
  font-size: 23rpx;
  color: #2f855a;
  line-height: 1.5;
}

/* ===== 通用卡片 ===== */
.card {
  margin: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  padding: 24rpx 26rpx 20rpx;
}
.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}
.card-title {
  font-size: 29rpx;
  font-weight: 600;
  color: #333;
  display: block;
}
.card-sub {
  font-size: 22rpx;
  color: #999;
}

/* ===== ① 归属确认 ===== */
.assign-block {
  padding: 18rpx 0 14rpx;
  border-bottom: 2rpx solid #f5f5f5;
}
.assign-block:last-child {
  border-bottom: none;
}
.assign-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-wrap: wrap;
}
.assign-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  flex-shrink: 0;
}
.assign-name {
  font-size: 27rpx;
  font-weight: 600;
  color: #333;
}
.assign-count {
  font-size: 21rpx;
  color: #999;
}
.assign-chips {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-left: auto;
}
.assign-chip {
  padding: 8rpx 22rpx;
  border-radius: 24rpx;
  background: #f3f4f6;
  border: 2rpx solid transparent;
}
.assign-chip--active {
  background: #ebf0ff;
  border-color: #5b8def;
}
.assign-chip-text {
  font-size: 23rpx;
  color: #555;
}
.assign-chip--active .assign-chip-text {
  color: #3b6fd4;
  font-weight: 600;
}
.assign-locked {
  font-size: 21rpx;
  color: #bbb;
}

.chip-scroll {
  margin-top: 12rpx;
  white-space: nowrap;
}
.chip-row {
  display: inline-flex;
  gap: 12rpx;
}
.chip {
  padding: 8rpx 22rpx;
  border-radius: 24rpx;
  background: #f8f8f8;
  border: 2rpx solid transparent;
}
.chip--active {
  background: #e8f8f0;
  border-color: #43b581;
}
.chip-text {
  font-size: 22rpx;
  color: #666;
}
.chip--active .chip-text {
  color: #2f855a;
  font-weight: 600;
}

.speaker-sample {
  font-size: 21rpx;
  color: #aaa;
  display: block;
  margin-top: 10rpx;
  line-height: 1.5;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== ② 时间线对话流 ===== */
.tl-list {
  margin-top: 12rpx;
}
.tl-row {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  padding: 14rpx 0;
  border-bottom: 2rpx solid #f7f7f7;
}
.tl-row:last-child {
  border-bottom: none;
}
.tl-time {
  flex-shrink: 0;
  width: 76rpx;
  font-size: 21rpx;
  color: #999;
  line-height: 1.9;
  font-variant-numeric: tabular-nums;
}
.tl-badge {
  flex-shrink: 0;
  max-width: 150rpx;
  border: 2rpx solid #5b8def;
  border-radius: 8rpx;
  padding: 2rpx 10rpx;
  background: #fff;
  margin-top: 4rpx;
}
.tl-badge-text {
  font-size: 21rpx;
  font-weight: 600;
  color: #5b8def;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 126rpx;
}
.tl-text {
  flex: 1;
  font-size: 26rpx;
  color: #444;
  line-height: 1.6;
}
.tl-del {
  flex-shrink: 0;
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #fdeef1;
  margin-top: 2rpx;
}
.tl-del-text {
  font-size: 28rpx;
  color: #f43f5e;
  line-height: 1;
}

/* ===== 提交 ===== */
.submit-wrap {
  padding: 28rpx 30rpx 40rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
}
.submit-btn {
  width: 100%;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 48rpx;
  background: linear-gradient(135deg, #5b8def, #3b6fd4);
  box-shadow: 0 6rpx 20rpx rgba(59, 111, 212, 0.35);
}
.submit-btn--disabled {
  opacity: 0.6;
}
.submit-btn-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
  letter-spacing: 4rpx;
}
.submit-hint {
  font-size: 22rpx;
  color: #999;
}

/* ===== 空态 ===== */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 28rpx;
  padding-top: 300rpx;
}
.empty-text {
  font-size: 28rpx;
  color: #999;
}
.empty-btn {
  padding: 16rpx 64rpx;
  border-radius: 40rpx;
  background: #5b8def;
}
.empty-btn-text {
  font-size: 28rpx;
  color: #fff;
  font-weight: 600;
}
</style>

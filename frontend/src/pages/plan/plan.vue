<template>
  <view class="page-container">
    <!-- 加载态 -->
    <view v-if="viewMode === 'loading'" class="center-state">
      <view class="spinner" />
      <text class="center-state-text">加载中…</text>
    </view>

    <!-- 空态：无画像数据 -->
    <view v-else-if="viewMode === 'empty'" class="center-state">
      <text class="empty-icon">📈</text>
      <text class="center-state-title">还没有能力数据</text>
      <text class="center-state-text">完成几次情景模拟训练后，AI 会为你定制专属提升计划</text>
      <view class="empty-actions">
        <view class="btn btn--primary" @tap="handleAssess">
          <text class="btn-text">{{ assessing ? '生成中…' : '立即生成计划' }}</text>
        </view>
        <view class="btn btn--secondary" @tap="goSimulation">
          <text class="btn-text">去模拟训练</text>
        </view>
      </view>
    </view>

    <!-- 正常态 -->
    <scroll-view v-else-if="profile" class="plan-scroll" scroll-y>
      <!-- ① 能力现状 -->
      <view class="card">
        <view class="card-header">
          <text class="card-title">能力现状</text>
          <text class="card-hint">累计 {{ profile.totalSessions }} 次训练</text>
        </view>
        <view class="status-body">
          <view class="score-main">
            <text class="score-number">{{ profile.overallScore }}</text>
            <text class="score-unit">综合分</text>
          </view>
          <view class="dims">
            <view v-for="d in DIMENSIONS" :key="d.key" class="dim-item">
              <text class="dim-label">{{ d.label }}</text>
              <view class="dim-bar">
                <view class="dim-bar-fill" :style="{ width: (profile as any)[d.key] + '%', backgroundColor: d.color }" />
              </view>
              <text class="dim-score">{{ (profile as any)[d.key] }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- ② 成长曲线（真实 scoreHistory 总分趋势） -->
      <view class="card">
        <view class="card-header">
          <text class="card-title">成长曲线</text>
          <text v-if="trendData.length >= 2" class="card-hint">
            {{ trendDelta >= 0 ? '↑' : '↓' }} {{ Math.abs(trendDelta) }} 分
          </text>
        </view>
        <view v-if="trendData.length >= 2" class="trend">
          <view
            v-for="(t, i) in trendData"
            :key="i"
            class="trend-col"
          >
            <text class="trend-value">{{ t.score }}</text>
            <view class="trend-bar-wrap">
              <view
                class="trend-bar"
                :class="{ 'trend-bar--last': i === trendData.length - 1 }"
                :style="{ height: barHeight(t.score) + '%' }"
              />
            </view>
            <text class="trend-label">{{ t.label }}</text>
          </view>
        </view>
        <view v-else class="trend-empty">
          <text class="trend-empty-text">完成更多训练后解锁趋势</text>
        </view>
      </view>

      <!-- ③ AI 综合评估 -->
      <view class="card" v-if="profile.assessment">
        <view class="card-header">
          <text class="card-title">AI 综合评估</text>
        </view>
        <text class="card-text">{{ profile.assessment }}</text>
        <view v-if="profile.topStrengths?.length" class="tag-row">
          <text class="tag-row-label">优势</text>
          <text v-for="s in profile.topStrengths" :key="s" class="tag tag--positive">{{ s }}</text>
        </view>
        <view v-if="profile.topWeaknesses?.length" class="tag-row">
          <text class="tag-row-label">短板</text>
          <text v-for="s in profile.topWeaknesses" :key="s" class="tag tag--suggest">{{ s }}</text>
        </view>
      </view>

      <!-- ④ 专属提升路线 -->
      <view class="card" v-if="profile.improvementPlan">
        <view class="card-header">
          <text class="card-title">专属提升路线</text>
        </view>
        <text class="card-text">{{ profile.improvementPlan }}</text>
      </view>

      <!-- ⑤ 本周目标（可打卡） -->
      <view class="card" v-if="profile.weeklyGoals?.length">
        <view class="card-header">
          <text class="card-title">本周目标</text>
          <text class="card-hint">已完成 {{ doneCount }}/{{ profile.weeklyGoals.length }}</text>
        </view>
        <view class="goal-list">
          <view
            v-for="(g, i) in profile.weeklyGoals"
            :key="i"
            class="goal-item"
            :class="{ 'goal-item--done': goalStatus[i] }"
            @tap="toggleGoal(i)"
          >
            <view class="goal-check">
              <text v-if="goalStatus[i]" class="goal-check-mark">✓</text>
            </view>
            <text class="goal-text" :class="{ 'goal-text--done': goalStatus[i] }">{{ g }}</text>
          </view>
        </view>
      </view>

      <!-- 底部操作 -->
      <view class="bottom-actions">
        <view class="btn btn--secondary" @tap="handleRefreshPlan">
          <text class="btn-text">{{ assessing ? '生成中…' : '刷新计划' }}</text>
        </view>
        <view class="btn btn--primary" @tap="goSimulation">
          <text class="btn-text">开始训练</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getAbilityProfile, updateWeeklyGoalsStatus } from '@/api/user'
import { assessProfile } from '@/api/simulation'
import type { AbilityProfile } from '@/types/user'

type ViewMode = 'loading' | 'empty' | 'normal'

const DIMENSIONS = [
  { key: 'avgClarity', label: '清晰度', color: '#06B6D4' },
  { key: 'avgLogicality', label: '逻辑性', color: '#8B5CF6' },
  { key: 'avgEmpathyListening', label: '共情倾听', color: '#F43F5E' },
  { key: 'avgInteractivity', label: '互动性', color: '#F59E0B' },
  { key: 'avgRelaxation', label: '松弛感', color: '#10B981' },
] as const

const viewMode = ref<ViewMode>('loading')
const profile = ref<AbilityProfile | null>(null)
const goalStatus = ref<boolean[]>([])
const assessing = ref(false)

// ── 成长曲线数据：scoreHistory 最近 ≤10 条，按时间正序 ──
const TREND_MAX = 10

const trendData = computed(() => {
  const history = profile.value?.scoreHistory ?? []
  const sorted = [...history].sort(
    (a, b) => new Date(a.scoredAt).getTime() - new Date(b.scoredAt).getTime(),
  )
  const recent = sorted.slice(-TREND_MAX)
  return recent.map((h, i) => ({
    score: h.score,
    label: recent.length > 5 ? `#${i + 1}` : h.theme.slice(0, 4),
  }))
})

const trendDelta = computed(() => {
  if (trendData.value.length < 2) return 0
  const first = trendData.value[0].score
  const last = trendData.value[trendData.value.length - 1].score
  return last - first
})

function barHeight(score: number): number {
  return Math.max(8, Math.min(100, score))
}

// ── 打卡 ──
const doneCount = computed(() => goalStatus.value.filter(Boolean).length)

function toggleGoal(index: number) {
  if (!profile.value) return
  const prev = [...goalStatus.value]
  goalStatus.value[index] = !goalStatus.value[index]
  updateWeeklyGoalsStatus({ userId: profile.value.userId, status: [...goalStatus.value] })
    .then((res) => {
      if (res.code === 0) {
        const done = goalStatus.value.filter(Boolean).length
        uni.showToast({
          title: `本周目标 ${done}/${profile.value?.weeklyGoals.length ?? 0}`,
          icon: 'none',
        })
      } else {
        goalStatus.value = prev
        uni.showToast({ title: '保存失败', icon: 'none' })
      }
    })
    .catch(() => {
      goalStatus.value = prev
      uni.showToast({ title: '网络异常', icon: 'none' })
    })
}

// ── 刷新计划（LLM 生成，重置打卡） ──
async function handleAssess() {
  if (assessing.value) return
  assessing.value = true
  try {
    const res = await assessProfile()
    if (res.code === 0 && res.data && (res.data as any).status === 'no_data') {
      uni.showToast({ title: '暂无训练数据，先完成一次模拟吧', icon: 'none' })
      return
    }
    if (res.code === 0) {
      await loadProfile()
      if (viewMode.value === 'normal') {
        uni.showToast({ title: '计划已更新', icon: 'success' })
      }
    } else {
      uni.showToast({ title: '生成失败，请重试', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '网络异常，请重试', icon: 'none' })
  } finally {
    assessing.value = false
  }
}

function handleRefreshPlan() {
  uni.showModal({
    title: '刷新计划',
    content: '将基于你的全部训练记录重新生成提升计划，本周打卡进度会重置',
    confirmText: '重新生成',
    success: (res) => {
      if (res.confirm) handleAssess()
    },
  })
}

function goSimulation() {
  uni.switchTab({ url: '/pages/simulation/simulation' })
}

// ── 加载 ──
async function loadProfile() {
  try {
    const res = await getAbilityProfile(1)
    if (res.code === 0 && res.data && res.data.totalSessions > 0) {
      profile.value = res.data
      // 打卡状态与 goals 对齐（旧数据缺 status 时补 false）
      const goals = res.data.weeklyGoals ?? []
      const status = res.data.weeklyGoalsStatus ?? []
      goalStatus.value = goals.map((_, i) => status[i] ?? false)
      viewMode.value = 'normal'
    } else {
      viewMode.value = 'empty'
    }
  } catch {
    viewMode.value = 'empty'
  }
}

onLoad(() => {
  loadProfile()
})

// 从模拟训练返回时刷新数据（训练后可能有新评分）
onShow(() => {
  if (viewMode.value !== 'loading') {
    loadProfile()
  }
})
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}

/* 居中态（加载/空） */
.center-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 60rpx;
  gap: 16rpx;
}

.empty-icon {
  font-size: 88rpx;
  margin-bottom: 8rpx;
}

.center-state-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
}

.center-state-text {
  font-size: 26rpx;
  color: #999;
  text-align: center;
  line-height: 1.6;
}

.empty-actions {
  margin-top: 32rpx;
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.spinner {
  width: 64rpx;
  height: 64rpx;
  border: 6rpx solid #e8e8e8;
  border-top-color: #5b8def;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 正常态 */
.plan-scroll {
  flex: 1;
  min-height: 0;
  padding: 20rpx 0 60rpx;
}

.card {
  margin: 0 24rpx 20rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  padding: 24rpx 28rpx;
}

.card-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
}

.card-hint {
  font-size: 22rpx;
  color: #999;
}

.card-text {
  font-size: 28rpx;
  color: #555;
  line-height: 1.7;
}

/* 能力现状 */
.status-body {
  display: flex;
  align-items: center;
}

.score-main {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 140rpx;
  flex-shrink: 0;
  margin-right: 28rpx;
}

.score-number {
  font-size: 64rpx;
  font-weight: bold;
  color: #5b8def;
  line-height: 1;
}

.score-unit {
  font-size: 24rpx;
  color: #999;
  margin-top: 6rpx;
}

.dims {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.dim-item {
  display: flex;
  align-items: center;
}

.dim-label {
  font-size: 24rpx;
  color: #666;
  width: 100rpx;
  flex-shrink: 0;
}

.dim-bar {
  flex: 1;
  height: 14rpx;
  background: #f0f0f0;
  border-radius: 7rpx;
  overflow: hidden;
  margin: 0 12rpx;
}

.dim-bar-fill {
  height: 100%;
  border-radius: 7rpx;
  transition: width 0.4s ease;
}

.dim-score {
  font-size: 24rpx;
  font-weight: 600;
  color: #333;
  width: 50rpx;
  text-align: right;
  flex-shrink: 0;
}

/* 成长曲线 */
.trend {
  display: flex;
  align-items: flex-end;
  gap: 12rpx;
  height: 240rpx;
  padding-top: 12rpx;
}

.trend-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}

.trend-value {
  font-size: 20rpx;
  color: #5b8def;
  font-weight: 600;
}

.trend-bar-wrap {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 6rpx 0;
}

.trend-bar {
  width: 28rpx;
  border-radius: 8rpx 8rpx 0 0;
  background: linear-gradient(180deg, #93c5fd, #bfdbfe);
  min-height: 8%;
}

.trend-bar--last {
  background: linear-gradient(180deg, #5b8def, #93c5fd);
}

.trend-label {
  font-size: 20rpx;
  color: #999;
  margin-top: 6rpx;
  white-space: nowrap;
  overflow: hidden;
  max-width: 100%;
}

.trend-empty {
  padding: 60rpx 0;
  display: flex;
  justify-content: center;
}

.trend-empty-text {
  font-size: 26rpx;
  color: #bbb;
}

/* 标签行 */
.tag-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 20rpx;
}

.tag-row-label {
  font-size: 24rpx;
  font-weight: 600;
  color: #666;
}

.tag {
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
}

.tag--positive {
  color: #43b581;
  background: #e8f8f0;
}

.tag--suggest {
  color: #f5a623;
  background: #fff6e5;
}

/* 周目标 */
.goal-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.goal-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx;
  background: #f8f8f8;
  border-radius: 12rpx;
  border: 2rpx solid transparent;
  transition: all 0.15s ease;
}

.goal-item--done {
  background: #ebf0ff;
  border-color: #5b8def;
}

.goal-check {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  border: 3rpx solid #cbd5e1;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.goal-item--done .goal-check {
  background: #5b8def;
  border-color: #5b8def;
}

.goal-check-mark {
  font-size: 24rpx;
  color: #fff;
  font-weight: 700;
  line-height: 1;
}

.goal-text {
  flex: 1;
  font-size: 28rpx;
  color: #444;
  line-height: 1.5;
}

.goal-text--done {
  color: #5b8def;
}

/* 按钮 */
.bottom-actions {
  display: flex;
  gap: 20rpx;
  padding: 12rpx 24rpx 40rpx;
}

.btn {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 44rpx;
}

.btn--primary {
  background: linear-gradient(135deg, #5b8def, #3b6fd4);
  box-shadow: 0 4rpx 16rpx rgba(59, 111, 212, 0.35);
}

.btn--primary .btn-text {
  color: #fff;
}

.btn--secondary {
  background: #f3f4f6;
  border: 1rpx solid #e5e7eb;
}

.btn--secondary .btn-text {
  color: #374151;
}

.btn-text {
  font-size: 28rpx;
  font-weight: 600;
}

.btn--primary:active,
.btn--secondary:active {
  opacity: 0.85;
}
</style>

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

    <!-- 正常态：三段式（今日 / 诊断 / 成长） -->
    <scroll-view v-else-if="profile" class="plan-scroll" scroll-y>
      <!-- 新徽章里程碑祝贺条（本地已读标记，一次性） -->
      <view v-if="milestone" class="milestone" @tap="milestone = ''">
        <text class="milestone-text">🎉 {{ milestone }}（点击关闭）</text>
      </view>
      <view class="seg-bar">
        <view
          v-for="s in SEGS"
          :key="s.key"
          class="seg-item"
          :class="{ 'seg-item--active': seg === s.key }"
          @tap="seg = s.key"
        >
          <text class="seg-text">{{ s.label }}</text>
        </view>
      </view>

      <!-- ══════════ 今日 ══════════ -->
      <view v-show="seg === 'today'">
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

        <!-- ② 训练处方（P2-9：结构化任务系统，取代旧周目标勾选清单） -->
        <WeekProgressRing v-if="planTasks.length" :done="planDone" :total="planTasks.length" :streak-days="streakDays" />
        <RecommendCard v-if="planCurrent?.recommendation" :rec="planCurrent.recommendation" />
        <view class="card" v-if="planTasks.length">
          <view class="card-header">
            <text class="card-title">本周训练处方</text>
            <text class="card-hint">{{ plan?.weekKey }}</text>
          </view>
          <view v-if="plan?.diagnosis?.summary" class="plan-note">
            <text class="plan-note-text">{{ plan.diagnosis.summary }}</text>
          </view>
          <TaskCard
            v-for="t in planTasks"
            :key="t.id"
            :task="t"
            @train="goTrain($event)"
            @submit="openSubmit($event)"
          />
        </view>
        <view v-else-if="planGenerating" class="card">
          <text class="card-text">🧠 AI 正在为你生成训练处方…（约 10~30 秒，稍后自动刷新）</text>
        </view>
        <view v-else-if="planInsufficient" class="card">
          <view class="card-header">
            <text class="card-title">训练处方未解锁</text>
          </view>
          <text class="card-text">完成 3 场带 AI 评分的情景模拟后，系统会基于你的子技能诊断开出处方。先去练一场吧。</text>
        </view>

        <!-- 底部操作 -->
        <view class="bottom-actions">
          <view class="btn btn--secondary" @tap="handleRegenerate">
            <text class="btn-text">刷新处方</text>
          </view>
          <view class="btn btn--primary" @tap="goSimulation">
            <text class="btn-text">开始训练</text>
          </view>
        </view>
      </view>

      <!-- ══════════ 诊断 ══════════ -->
      <view v-show="seg === 'diag'">
        <!-- AI 综合评估 -->
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

        <!-- 专属提升路线 -->
        <view class="card" v-if="profile.improvementPlan">
          <view class="card-header">
            <text class="card-title">专属提升路线</text>
          </view>
          <text class="card-text">{{ profile.improvementPlan }}</text>
        </view>

        <!-- 子技能下钻（雷达 → 子技能 → 证据） -->
        <view class="card">
          <view class="card-header">
            <text class="card-title">能力诊断</text>
            <text class="card-hint">点维度/子技能下钻原话证据</text>
          </view>
          <SkillDrilldown :profile="profile" />
        </view>

        <!-- 评估文本再生成（LLM；不触碰处方与打卡） -->
        <view class="bottom-actions">
          <view class="btn btn--secondary" @tap="handleRefreshPlan">
            <text class="btn-text">{{ assessing ? '生成中…' : '刷新评估' }}</text>
          </view>
        </view>
      </view>

      <!-- ══════════ 成长 ══════════ -->
      <view v-show="seg === 'growth'">
        <!-- 趋势（总分 + 16 子技能切换，P3-4） -->
        <view class="card">
          <view class="card-header">
            <text class="card-title">成长趋势</text>
            <text class="card-hint">点标签切换对象</text>
          </view>
          <TrendChart :profile="profile" />
        </view>

        <!-- 维度等级（前端现算派生量，不落库） -->
        <view class="card">
          <view class="card-header">
            <text class="card-title">维度等级</text>
          </view>
          <view class="dim-levels">
            <view v-for="d in DIMENSIONS" :key="d.key" class="dim-level-row">
              <text class="dim-level-name">{{ d.label }}</text>
              <text class="dim-level-lv">Lv{{ dimLevel((profile as any)[d.key]) }}</text>
              <view class="dim-level-bar">
                <view class="dim-level-fill" :style="{ width: (profile as any)[d.key] + '%', backgroundColor: d.color }" />
              </view>
            </view>
          </view>
        </view>

        <!-- 训练连续性 -->
        <view class="card">
          <view class="card-header">
            <text class="card-title">训练连续性</text>
          </view>
          <view class="streak-row">
            <text class="streak-fire">🔥</text>
            <text class="streak-days">连续 {{ profile.streakDays ?? 0 }} 天</text>
            <text class="streak-best">历史最高 {{ profile.bestStreak ?? 0 }} 天</text>
          </view>
        </view>

        <!-- 徽章墙 -->
        <view class="card">
          <view class="card-header">
            <text class="card-title">徽章墙</text>
            <text class="card-hint">近3场均分≥80掌握 / ≥90精通</text>
          </view>
          <BadgeWall :badges="profile.badges" />
        </view>

        <!-- 周报时间轴（横滑） -->
        <view class="card" v-if="reports.length">
          <view class="card-header">
            <text class="card-title">周报时间轴</text>
            <text class="card-hint">最新一份可采纳下周草稿</text>
          </view>
          <scroll-view scroll-x class="report-scroll">
            <view class="report-track">
              <WeeklyReportCard
                v-for="(r, i) in reports"
                :key="r.weekKey"
                :report="r"
                :interactive="i === 0"
                @changed="loadReports"
              />
            </view>
          </scroll-view>
        </view>
      </view>
    </scroll-view>

    <!-- 处方任务提交弹层（P2-9） -->
    <TaskSubmitPopup
      :visible="popupVisible"
      :task="popupTask"
      @close="popupVisible = false"
      @submitted="loadPlan"
    />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getAbilityProfile } from '@/api/user'
import { assessProfile } from '@/api/simulation'
import { getPlanCurrent, regeneratePlan, getPlanRecommendation } from '@/api/plan'
import type { AbilityProfile } from '@/types/user'
import type { PlanCurrent, PlanTask } from '@/types/plan'
import { setSimFocus } from '@/store/simFocus'
import SkillDrilldown from '@/components/plan/SkillDrilldown.vue'
import WeekProgressRing from '@/components/plan/WeekProgressRing.vue'
import RecommendCard from '@/components/plan/RecommendCard.vue'
import TaskCard from '@/components/plan/TaskCard.vue'
import TaskSubmitPopup from '@/components/plan/TaskSubmitPopup.vue'
import TrendChart from '@/components/plan/TrendChart.vue'
import BadgeWall from '@/components/plan/BadgeWall.vue'
import WeeklyReportCard from '@/components/plan/WeeklyReportCard.vue'
import { getReports } from '@/api/plan'
import { subSkillCn } from '@/constants/subSkills'
import type { WeeklyReport } from '@/types/plan'

type ViewMode = 'loading' | 'empty' | 'normal'
type SegKey = 'today' | 'diag' | 'growth'

const SEGS: { key: SegKey; label: string }[] = [
  { key: 'today', label: '今日' },
  { key: 'diag', label: '诊断' },
  { key: 'growth', label: '成长' },
]

const DIMENSIONS = [
  { key: 'avgClarity', label: '清晰度', color: '#06B6D4' },
  { key: 'avgLogicality', label: '逻辑性', color: '#8B5CF6' },
  { key: 'avgEmpathyListening', label: '共情倾听', color: '#F43F5E' },
  { key: 'avgInteractivity', label: '互动性', color: '#F59E0B' },
  { key: 'avgRelaxation', label: '松弛感', color: '#10B981' },
] as const

const viewMode = ref<ViewMode>('loading')
const profile = ref<AbilityProfile | null>(null)
const assessing = ref(false)
const seg = ref<SegKey>('today')

// ── 成长段（P3-4）：周报 / 里程碑 / 维度等级 ──
const reports = ref<WeeklyReport[]>([])
const milestone = ref('')

async function loadReports() {
  try {
    const res = await getReports(8)
    if (res.code === 0) {
      reports.value = res.data.items ?? []
    }
  } catch {
    /* 静默 */
  }
}

/** 维度等级（前端现算派生量，设计文档 §5.3 阈值） */
function dimLevel(avg: number): number {
  const v = Number(avg ?? 0)
  if (v >= 85) return 5
  if (v >= 70) return 4
  if (v >= 55) return 3
  if (v >= 40) return 2
  return 1
}

/** 新徽章里程碑：与本地已读集合 diff，一次性祝贺条 */
function checkMilestones() {
  const badges = profile.value?.badges ?? []
  if (!badges.length) return
  const seenKey = 'seenBadges'
  const seen: string[] = uni.getStorageSync(seenKey) || []
  const fresh = badges.filter((b) => !seen.includes(`${b.subSkill}:${b.level}`))
  if (fresh.length) {
    milestone.value = fresh
      .map((b) => `恭喜解锁「${subSkillCn(b.subSkill)}」${b.level >= 2 ? '精通' : '掌握'}徽章`)
      .join('；')
    uni.setStorageSync(seenKey, [...seen, ...fresh.map((b) => `${b.subSkill}:${b.level}`)])
  }
}

// ── 训练处方（P2-9）──
const planCurrent = ref<PlanCurrent | null>(null)
const plan = computed(() => planCurrent.value?.plan ?? null)
const planTasks = computed(() => plan.value?.tasks ?? [])
const planDone = computed(() => planTasks.value.filter((t) => t.status === 'done').length)
const streakDays = computed(() => planCurrent.value?.stats?.streakDays ?? 0)
const planGenerating = computed(() => !!planCurrent.value?.generating)
const planInsufficient = computed(() => !!planCurrent.value?.insufficientData)
const popupVisible = ref(false)
const popupTask = ref<PlanTask | null>(null)
let planPollTimer: ReturnType<typeof setInterval> | null = null

async function loadPlan() {
  try {
    const res = await getPlanCurrent()
    if (res.code === 0) {
      planCurrent.value = res.data
      if (res.data.generating) startPlanPoll()
    }
  } catch {
    /* 处方不可用零打扰：不影响评估/诊断/成长段 */
  }
}

/** generating 态轮询：2s 一次，上限 30s（防单飞锁长挂） */
function startPlanPoll() {
  stopPlanPoll()
  let n = 0
  planPollTimer = setInterval(async () => {
    if (++n > 15) {
      stopPlanPoll()
      return
    }
    try {
      const res = await getPlanCurrent()
      if (res.code === 0) {
        planCurrent.value = res.data
        if (!res.data.generating) stopPlanPoll()
      }
    } catch {
      /* ignore */
    }
  }, 2000)
}

function stopPlanPoll() {
  if (planPollTimer) {
    clearInterval(planPollTimer)
    planPollTimer = null
  }
}

/** 任务卡"去训练"：按任务子技能取后端映射行 → 写 store → switchTab 直启 */
async function goTrain(task?: PlanTask) {
  const subSkill = task?.subSkill ?? plan.value?.diagnosis?.focusSubSkills?.[0]
  if (!subSkill) return
  try {
    const res = await getPlanRecommendation(subSkill)
    const rec = res.code === 0 ? res.data.recommendation : null
    if (!rec) {
      uni.showToast({ title: '暂无法定位训练映射，请自由训练', icon: 'none' })
      return
    }
    setSimFocus({
      focusSubSkill: rec.subSkill,
      cnName: rec.cnName,
      theme: rec.theme,
      personality: rec.personality,
      prescriptionTaskId: rec.taskId,
    })
    uni.switchTab({ url: '/pages/simulation/simulation' })
  } catch {
    uni.showToast({ title: '网络异常', icon: 'none' })
  }
}

function openSubmit(task: PlanTask) {
  popupTask.value = task
  popupVisible.value = true
}

/** 刷新处方（旧计划归档，弹窗明示） */
function handleRegenerate() {
  uni.showModal({
    title: '刷新处方',
    content: '将基于最新训练数据重新生成本周处方，当前任务进度会归档',
    confirmText: '重新生成',
    success: async (r) => {
      if (!r.confirm) return
      try {
        const res = await regeneratePlan()
        if (res.code === 0 && res.data.status === 'ok') {
          uni.showToast({ title: '新处方已生成', icon: 'success' })
        } else if (res.code === 0 && res.data.status === 'insufficient_data') {
          uni.showToast({ title: '样本不足，先完成几场训练吧', icon: 'none' })
        } else {
          uni.showToast({ title: '生成失败，请稍后重试', icon: 'none' })
        }
      } catch {
        uni.showToast({ title: '网络异常', icon: 'none' })
      } finally {
        loadPlan()
      }
    },
  })
}

// ── 刷新评估（LLM 生成评估文本，打卡进度保留） ──
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
    title: '刷新评估',
    content: '将基于你的全部训练记录重新生成能力评估与提升路线，本周打卡进度会保留',
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
  loadPlan()
  loadReports()
})

// 从模拟训练返回时刷新（训练评分 → 统计/处方进度自动回销）
onShow(() => {
  if (viewMode.value !== 'loading') {
    loadProfile().then(() => checkMilestones())
  }
  loadPlan()
  loadReports()
})

onUnmounted(() => stopPlanPoll())
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

/* 三段式 segmented control */
.seg-bar {
  display: flex;
  margin: 0 24rpx 20rpx;
  background: #fff;
  border-radius: 44rpx;
  padding: 6rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
}
.seg-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 68rpx;
  border-radius: 38rpx;
  transition: background 0.2s ease;
}
.seg-item--active {
  background: linear-gradient(135deg, #5b8def, #3b6fd4);
}
.seg-text {
  font-size: 26rpx;
  color: #666;
  font-weight: 500;
}
.seg-item--active .seg-text {
  color: #fff;
  font-weight: 600;
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

.goal-progress {
  height: 12rpx;
  background: #f0f0f0;
  border-radius: 6rpx;
  overflow: hidden;
}

.goal-progress-fill {
  height: 100%;
  border-radius: 6rpx;
  background: linear-gradient(90deg, #93c5fd, #5b8def);
  transition: width 0.4s ease;
}

.goal-item {
  display: flex;
  align-items: flex-start;
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
  margin-top: 2rpx;
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

.goal-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
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

.goal-note {
  font-size: 22rpx;
  color: #8a94a6;
  line-height: 1.4;
}

/* 成长段 */
.streak-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.streak-fire {
  font-size: 40rpx;
}

.streak-days {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
}

.streak-best {
  font-size: 24rpx;
  color: #999;
}

.growth-todo {
  display: flex;
  justify-content: center;
}

.growth-todo-text {
  font-size: 24rpx;
  color: #b0b8c4;
}

/* P3-4 里程碑祝贺条 */
.milestone {
  margin: 0 24rpx 16rpx;
  background: linear-gradient(135deg, #fff7e8, #ffe9c4);
  border: 2rpx solid #ffd79a;
  border-radius: 14rpx;
  padding: 16rpx 22rpx;
}
.milestone-text {
  font-size: 24rpx;
  color: #9a6b1f;
  line-height: 1.5;
}

/* P3-4 维度等级 */
.dim-levels {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}
.dim-level-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
}
.dim-level-name {
  width: 100rpx;
  font-size: 24rpx;
  color: #666;
  flex-shrink: 0;
}
.dim-level-lv {
  width: 64rpx;
  font-size: 24rpx;
  font-weight: 700;
  color: #5b8def;
}
.dim-level-bar {
  flex: 1;
  height: 12rpx;
  background: #f0f0f0;
  border-radius: 6rpx;
  overflow: hidden;
}
.dim-level-fill {
  height: 100%;
  border-radius: 6rpx;
  transition: width 0.4s ease;
}

/* P3-4 周报时间轴 */
.report-scroll {
  width: 100%;
  white-space: nowrap;
}
.report-track {
  display: flex;
  flex-direction: row;
}

/* 处方诊断摘要 */
.plan-note {
  background: #f2f6ff;
  border-radius: 10rpx;
  padding: 14rpx 18rpx;
  margin-bottom: 16rpx;
}

.plan-note-text {
  font-size: 24rpx;
  color: #3d5a99;
  line-height: 1.6;
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

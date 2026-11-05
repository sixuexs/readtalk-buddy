<template>
  <view class="home-page">
    <view class="home-scroll">
      <!-- ===== Hero：品牌区 ===== -->
      <view class="hero">
        <view class="hero-blob hero-blob--a" />
        <view class="hero-blob hero-blob--b" />

        <view class="hero-top">
          <view class="hero-avatar">
            <text class="hero-avatar-text">📖</text>
          </view>
          <view class="hero-greet">
            <text class="hero-hello">{{ greeting }}</text>
            <text class="hero-slogan">让每一次真实的交流，都更有分寸</text>
          </view>
        </view>

        <view class="hero-stats">
          <view class="hero-stat">
            <text class="hero-stat-num">{{ scoreText }}</text>
            <text class="hero-stat-label">综合分</text>
          </view>
          <view class="hero-divider" />
          <view class="hero-stat">
            <text class="hero-stat-num">{{ sessionsText }}</text>
            <text class="hero-stat-label">已练场次</text>
          </view>
          <view class="hero-divider" />
          <view class="hero-stat">
            <text class="hero-stat-num">{{ planBadge || '—' }}</text>
            <text class="hero-stat-label">本周任务</text>
          </view>
        </view>
      </view>

      <!-- ===== 能力雷达 ===== -->
      <view class="section-card section-card--radar">
        <view class="section-head">
          <view class="section-title-wrap">
            <view class="title-dot title-dot--blue" />
            <text class="section-title">能力雷达</text>
          </view>
          <text class="section-hint">五维能力</text>
        </view>
        <view class="radar-body">
          <view class="chart-wrap chart-wrap--radar">
            <!-- #ifdef H5 -->
            <view :id="radarDomId" class="chart-surface" />
            <!-- #endif -->
            <!-- #ifndef H5 -->
            <canvas
              :id="radarCanvasId"
              type="2d"
              class="chart-surface"
            />
            <!-- #endif -->
          </view>
          <view class="radar-list">
            <view v-for="(ind, i) in RADAR_INDICATORS" :key="ind.key" class="radar-item">
              <view class="radar-item-top">
                <text class="radar-item-name">{{ ind.name }}</text>
                <text class="radar-item-num">{{ radarRounded[i] }}</text>
              </view>
              <view class="radar-bar">
                <view class="radar-bar__fill" :style="{ width: (radarValues[i] ?? 0) + '%' }" />
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- ===== 趋势变化 ===== -->
      <view class="section-card">
        <view class="section-head">
          <view class="section-title-wrap">
            <view class="title-dot title-dot--mint" />
            <text class="section-title">趋势变化</text>
          </view>
          <text class="section-hint">综合分走势</text>
        </view>
        <view class="chart-wrap chart-wrap--line">
          <!-- #ifdef H5 -->
          <view :id="lineDomId" class="chart-surface" />
          <!-- #endif -->
          <!-- #ifndef H5 -->
          <canvas
            :id="lineCanvasId"
            type="2d"
            class="chart-surface"
          />
          <!-- #endif -->
        </view>
      </view>

      <!-- ===== 快捷功能 ===== -->
      <view class="section-card section-card--features">
        <view class="section-head">
          <view class="section-title-wrap">
            <view class="title-dot title-dot--pink" />
            <text class="section-title">快捷功能</text>
          </view>
          <text class="section-hint">一键直达</text>
        </view>
        <view class="feature-grid">
          <view
            v-for="item in featureItems"
            :key="item.key"
            class="feature-item"
            @click="item.action"
          >
            <view
              class="feature-icon-wrap"
              :style="{ background: item.bgColor, boxShadow: item.shadowColor }"
            >
              <text class="feature-icon-text">{{ item.iconText }}</text>
            </view>
            <text class="feature-label">{{ item.label }}</text>
            <text v-if="item.subtitle" class="feature-subtitle">{{ item.subtitle }}</text>
          </view>
        </view>
      </view>
    </view>

    <CustomTabBar />
    <FloatingActionButton />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, getCurrentInstance } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import * as echarts from 'echarts'
import CustomTabBar from '@/components/CustomTabBar.vue'
import FloatingActionButton from '@/components/FloatingActionButton.vue'
import { BASE_URL } from '@/config'
import { getPlanCurrent } from '@/api/plan'
// #ifndef H5
import WxCanvas from '@/utils/wx-canvas'
// #endif

// ── Unique DOM/Canvas IDs (per component instance) ──
const instance = getCurrentInstance()
const uid = instance?.uid ?? Math.floor(Math.random() * 1e6)
const radarDomId = `radar-chart-${uid}`
const radarCanvasId = `radar-canvas-${uid}`
const lineDomId = `line-chart-${uid}`
const lineCanvasId = `line-canvas-${uid}`

// ── Chart instances ──
let radarChart: echarts.ECharts | null = null
let lineChart: echarts.ECharts | null = null

// ── Radar indicator names & API field keys ──
const RADAR_INDICATORS = [
  { name: '清晰度', max: 100, key: 'avgClarity' },
  { name: '逻辑性', max: 100, key: 'avgLogicality' },
  { name: '共情倾听', max: 100, key: 'avgEmpathyListening' },
  { name: '互动性', max: 100, key: 'avgInteractivity' },
  { name: '松弛感', max: 100, key: 'avgRelaxation' },
] as const

const RADAR_FALLBACK: number[] = [60, 70, 65, 55, 75]

/** 雷达右侧分数条使用的实时五维值 */
const radarValues = ref<number[]>([...RADAR_FALLBACK])

/** 四舍五入后的展示值（避免模板内直接调用 Math） */
const radarRounded = computed(() =>
  RADAR_INDICATORS.map((_, i) => Math.round(radarValues.value[i] ?? 0)),
)

// ── Hero 区数据 ──
const overallScore = ref<number | null>(null)
const totalSessions = ref<number | null>(null)

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  if (h < 23) return '晚上好'
  return '夜深了'
})

const scoreText = computed(() =>
  overallScore.value === null ? '—' : String(Math.round(overallScore.value)),
)
const sessionsText = computed(() =>
  totalSessions.value === null ? '—' : String(totalSessions.value),
)

// ── Feature grid items ──
interface FeatureItem {
  key: string
  label: string
  subtitle?: string
  iconText: string
  bgColor: string
  shadowColor: string
  action: () => void
}

/** 本周处方任务角标（done/total）；处方不可用时静默隐藏 */
const planBadge = ref('')

async function loadPlanBadge() {
  try {
    const res = await getPlanCurrent()
    const tasks = res.code === 0 ? res.data?.plan?.tasks ?? null : null
    planBadge.value = tasks && tasks.length
      ? `${tasks.filter((t) => t.status === 'done').length}/${tasks.length}`
      : ''
  } catch {
    planBadge.value = ''
  }
}

const featureItems = computed<FeatureItem[]>(() => [
  {
    key: 'assist',
    label: '沟通辅助',
    subtitle: '录音复盘',
    iconText: '🎤',
    bgColor: 'linear-gradient(145deg, #FFE8F2, #FFD6E8)',
    shadowColor: '0 6rpx 16rpx rgba(255, 126, 179, 0.24)',
    action: () => uni.navigateTo({ url: '/pages/assist/record' }),
  },
  {
    key: 'simulation',
    label: '模拟训练',
    subtitle: '情景对话',
    iconText: '🎭',
    bgColor: 'linear-gradient(145deg, #E6FBF3, #CFF5E7)',
    shadowColor: '0 6rpx 16rpx rgba(46, 207, 160, 0.24)',
    action: () => uni.switchTab({ url: '/pages/simulation/simulation' }),
  },
  {
    key: 'promotion',
    label: '提升计划',
    subtitle: planBadge.value || '每周目标',
    iconText: '🎯',
    bgColor: 'linear-gradient(145deg, #FFF3E0, #FFE7C7)',
    shadowColor: '0 6rpx 16rpx rgba(255, 138, 92, 0.24)',
    action: () => uni.navigateTo({ url: '/pages/plan/plan' }),
  },
  {
    key: 'relation',
    label: '关系图谱',
    subtitle: '书友网络',
    iconText: '🌐',
    bgColor: 'linear-gradient(145deg, #E8F4FF, #D2E9FF)',
    shadowColor: '0 6rpx 16rpx rgba(59, 158, 255, 0.24)',
    action: () => uni.switchTab({ url: '/pages/relation/graph' }),
  },
  {
    key: 'icebreak',
    label: '破冰分析',
    subtitle: '初次见面',
    iconText: '✨',
    bgColor: 'linear-gradient(145deg, #F3EEFF, #E5DBFF)',
    shadowColor: '0 6rpx 16rpx rgba(167, 139, 250, 0.24)',
    action: () => uni.navigateTo({ url: '/pages/icebreak/icebreak' }),
  },
  {
    key: 'social',
    label: '会话记录',
    subtitle: '复盘回看',
    iconText: '💬',
    bgColor: 'linear-gradient(145deg, #E6FBFF, #CCF3FB)',
    shadowColor: '0 6rpx 16rpx rgba(34, 199, 233, 0.24)',
    action: () => uni.switchTab({ url: '/pages/social/social' }),
  },
])

// ── Trend（真实数据：scoreHistory 总分趋势，最多 10 条） ──
const TREND_MAX = 10
const trendPoints = ref<{ label: string; score: number }[]>([])

// ── Fetch user profile for hero stats + radar + trend ──
async function fetchProfileData(): Promise<{
  radar: number[]
  trend: { label: string; score: number }[]
}> {
  return new Promise((resolve) => {
    uni.request({
      url: `${BASE_URL}/api/user/profile?userId=1`,
      method: 'GET',
      success: (res: any) => {
        // 后端统一响应 { code, data }，画像文档在 data.data —— 缺这层解包会导致
        // 雷达恒走 fallback、趋势恒空（历史 bug）
        const body = res.data as { code?: number; data?: Record<string, unknown> } | undefined
        const data = body?.code === 0 ? body.data : undefined
        if (data && typeof data === 'object') {
          const values = RADAR_INDICATORS.map((ind) => {
            const v = (data as Record<string, unknown>)[ind.key]
            return typeof v === 'number' ? v : 0
          })
          const radar = values.some((v) => v > 0) ? values : RADAR_FALLBACK
          radarValues.value = radar

          const history = Array.isArray((data as any).scoreHistory)
            ? ((data as any).scoreHistory as { score: number; scoredAt: string }[])
            : []
          const sorted = [...history]
            .filter((h) => typeof h.score === 'number')
            .sort(
              (a, b) =>
                new Date(a.scoredAt).getTime() - new Date(b.scoredAt).getTime(),
            )
          const trend = sorted.slice(-TREND_MAX).map((h, i) => ({
            label: sorted.length > 5 ? `#${i + 1}` : String((h as any).theme ?? '').slice(0, 4),
            score: h.score,
          }))

          // Hero 数据（缺失时保持 null，展示占位符）
          const ov = (data as Record<string, unknown>).overallScore
          overallScore.value = typeof ov === 'number' ? ov : null
          const ts = (data as Record<string, unknown>).totalSessions
          totalSessions.value = typeof ts === 'number' ? ts : null

          resolve({ radar, trend })
          return
        }
        resolve({ radar: RADAR_FALLBACK, trend: [] })
      },
      fail: () => {
        resolve({ radar: RADAR_FALLBACK, trend: [] })
      },
    })
  })
}

// ── Build echarts radar option ──
function buildRadarOption(radarValues: number[]) {
  return {
    radar: {
      indicator: RADAR_INDICATORS.map((ind) => ({ name: ind.name, max: ind.max })),
      center: ['50%', '54%'],
      radius: '58%',
      axisName: {
        color: '#5C6B7A',
        fontSize: 11,
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(59,158,255,0.03)', 'rgba(59,158,255,0.02)'],
        },
      },
    },
    series: [
      {
        type: 'radar',
        data: [{ value: radarValues, name: '当前能力' }],
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { color: '#3B9EFF', width: 2 },
        areaStyle: { color: 'rgba(59,158,255,0.12)' },
        itemStyle: { color: '#3B9EFF' },
      },
    ],
  }
}

// ── Build echarts line option（真实总分趋势） ──
function buildLineOption() {
  const points = trendPoints.value

  if (points.length < 2) {
    // 数据不足：显示占位提示
    return {
      title: {
        text: '完成更多训练后解锁趋势',
        left: 'center',
        top: 'middle',
        textStyle: { color: '#B0BCCB', fontSize: 13, fontWeight: 'normal' },
      },
    }
  }

  return {
    tooltip: { trigger: 'axis' },
    grid: { top: 24, right: 20, bottom: 28, left: 40 },
    xAxis: {
      type: 'category',
      data: points.map((p) => p.label),
      axisLine: { lineStyle: { color: '#E2E8F0' } },
      axisTick: { show: false },
      axisLabel: { color: '#8E9DAB', fontSize: 10 },
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      splitLine: { lineStyle: { color: '#F0F4F8', type: 'dashed' as const } },
      axisLabel: { color: '#8E9DAB', fontSize: 10 },
    },
    series: [
      {
        name: '综合分',
        type: 'line',
        data: points.map((p) => p.score),
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#3B9EFF', width: 2 },
        itemStyle: { color: '#3B9EFF' },
        areaStyle: { color: 'rgba(59, 158, 255, 0.08)' },
        label: { show: true, fontSize: 10, color: '#5C6B7A' },
      },
    ],
  }
}

// ── H5 chart initializer ──
async function initH5Chart(domId: string): Promise<echarts.ECharts | null> {
  await nextTick()
  let el = document.getElementById(domId)
  if (!el) {
    await new Promise((r) => setTimeout(r, 120))
    el = document.getElementById(domId)
  }
  if (!el) return null
  return echarts.init(el)
}

// ── Mini-program canvas helper ──
// #ifndef H5
function initMiniCanvas(
  canvasId: string,
): Promise<{ chart: echarts.ECharts; wxCanvas: InstanceType<typeof WxCanvas> } | null> {
  return new Promise((resolve) => {
    const proxy = instance?.proxy
    const query = proxy
      ? uni.createSelectorQuery().in(proxy)
      : uni.createSelectorQuery()
    query
      .select(`#${canvasId}`)
      .fields({ node: true, size: true }, () => {})
      .exec((res: any) => {
        const info = res?.[0] as
          | { node?: { getContext: (type: string) => CanvasRenderingContext2D }; width?: number; height?: number }
          | undefined
        const canvasNode = info?.node
        if (!canvasNode) {
          resolve(null)
          return
        }
        const dpr = uni.getSystemInfoSync().pixelRatio || 2
        const w = info.width || 300
        const h = info.height || 300
        ;(canvasNode as any).width = w * dpr
        ;(canvasNode as any).height = h * dpr

        const ctx = canvasNode.getContext('2d')
        const wxCanvas = new WxCanvas(ctx, canvasId, true, canvasNode)
        const chart = echarts.init(wxCanvas as any, null, {
          width: w,
          height: h,
          devicePixelRatio: dpr,
        })
        wxCanvas.setChart(chart)
        resolve({ chart, wxCanvas })
      })
  })
}
// #endif

// ── Initialize both charts ──
async function initCharts() {
  const { radar, trend } = await fetchProfileData()
  trendPoints.value = trend
  const radarOption = buildRadarOption(radar)
  const lineOption = buildLineOption()

  // #ifdef H5
  const h5Radar = await initH5Chart(radarDomId)
  if (h5Radar) {
    h5Radar.setOption(radarOption)
    radarChart = h5Radar
  }

  const h5Line = await initH5Chart(lineDomId)
  if (h5Line) {
    h5Line.setOption(lineOption)
    lineChart = h5Line
  }
  // #endif

  // #ifndef H5
  const radarRes = await initMiniCanvas(radarCanvasId)
  if (radarRes) {
    radarRes.chart.setOption(radarOption)
    radarChart = radarRes.chart
  }

  const lineRes = await initMiniCanvas(lineCanvasId)
  if (lineRes) {
    lineRes.chart.setOption(lineOption)
    lineChart = lineRes.chart
  }
  // #endif
}

// ── Refresh charts on page show（评分/训练后返回首页自动更新） ──
async function refreshCharts() {
  // 首次进入由 onMounted 初始化承担；chart 未就绪时跳过
  if (!radarChart && !lineChart) return
  const { radar, trend } = await fetchProfileData()
  trendPoints.value = trend
  // notMerge=true：在「占位提示 ↔ 真实曲线」间整体切换
  radarChart?.setOption(buildRadarOption(radar), true)
  lineChart?.setOption(buildLineOption(), true)
}

// ── Lifecycle ──
onMounted(() => {
  setTimeout(() => {
    initCharts()
  }, 120)
})

onShow(() => {
  refreshCharts()
  loadPlanBadge()
})

onUnmounted(() => {
  radarChart?.dispose()
  radarChart = null
  lineChart?.dispose()
  lineChart = null
})
</script>

<style scoped>
/* ========== Page Layout ========== */
.home-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: linear-gradient(165deg, #fff0f8 0%, #f0f6ff 45%, #f2fff8 100%);
}

.home-scroll {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 20rpx 24rpx;
  padding-bottom: 110rpx;
  box-sizing: border-box;
  overflow: hidden;
}

/* ========== Hero ========== */
.hero {
  position: relative;
  overflow: hidden;
  border-radius: 24rpx;
  padding: 26rpx 28rpx 22rpx;
  margin-bottom: 16rpx;
  background: linear-gradient(135deg, #5f9dff 0%, #8f86ff 52%, #ff9cc8 100%);
  box-shadow: 0 10rpx 28rpx rgba(107, 143, 255, 0.28);
}

.hero-blob {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
}

.hero-blob--a {
  width: 220rpx;
  height: 220rpx;
  right: -70rpx;
  top: -90rpx;
}

.hero-blob--b {
  width: 140rpx;
  height: 140rpx;
  right: 60rpx;
  bottom: -80rpx;
  background: rgba(255, 255, 255, 0.1);
}

.hero-top {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.hero-avatar {
  width: 76rpx;
  height: 76rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.24);
  border: 2rpx solid rgba(255, 255, 255, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.hero-avatar-text {
  font-size: 38rpx;
  line-height: 1;
}

.hero-greet {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 0;
}

.hero-hello {
  font-size: 34rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 1rpx;
  text-shadow: 0 2rpx 8rpx rgba(60, 80, 160, 0.25);
}

.hero-slogan {
  font-size: 21rpx;
  color: rgba(255, 255, 255, 0.9);
  letter-spacing: 0.5rpx;
}

.hero-stats {
  display: flex;
  align-items: center;
  margin-top: 22rpx;
  padding-top: 18rpx;
  border-top: 2rpx solid rgba(255, 255, 255, 0.22);
}

.hero-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.hero-divider {
  width: 2rpx;
  height: 46rpx;
  background: rgba(255, 255, 255, 0.26);
}

.hero-stat-num {
  font-size: 40rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.hero-stat-label {
  font-size: 19rpx;
  color: rgba(255, 255, 255, 0.82);
}

/* ========== Section Card ========== */
.section-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
  background: #ffffff;
  border-radius: 20rpx;
  padding: 18rpx 16rpx 14rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(59, 158, 255, 0.06);
}

.section-card--features {
  flex: 1.25;
  min-height: 0;
}

.section-card--radar {
  flex: 1.12;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.section-title-wrap {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.title-dot {
  width: 8rpx;
  height: 24rpx;
  border-radius: 4rpx;
}

.title-dot--blue {
  background: #3b9eff;
}
.title-dot--mint {
  background: #2ecfa0;
}
.title-dot--pink {
  background: #ff7eb3;
}

.section-title {
  font-size: 27rpx;
  font-weight: 700;
  color: #2a3441;
  letter-spacing: 0.5rpx;
}

.section-hint {
  font-size: 19rpx;
  color: #a9b6c4;
}

/* ========== Chart Containers ========== */
.chart-wrap {
  flex: 1;
  min-height: 0;
  width: 100%;
  position: relative;
  overflow: hidden;
  box-sizing: border-box;
}

.chart-surface {
  width: 100%;
  height: 100%;
  display: block;
  position: relative;
  z-index: 0;
}

/* ========== Radar + 五维分数条 ========== */
.radar-body {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: stretch;
  gap: 6rpx;
}

.chart-wrap--radar {
  flex: 1;
  min-width: 0;
}

.radar-list {
  width: 216rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12rpx;
  padding-right: 6rpx;
}

.radar-item-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.radar-item-name {
  font-size: 20rpx;
  color: #5c6b7a;
}

.radar-item-num {
  font-size: 22rpx;
  font-weight: 700;
  color: #2a3441;
  font-variant-numeric: tabular-nums;
}

.radar-bar {
  height: 8rpx;
  background: #f0f4f8;
  border-radius: 4rpx;
  overflow: hidden;
  margin-top: 4rpx;
}

.radar-bar__fill {
  height: 100%;
  border-radius: 4rpx;
  background: linear-gradient(90deg, #6bb8ff, #3b9eff);
  transition: width 0.3s ease;
}

/* ========== Feature Grid (2×3) ========== */
.feature-grid {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-wrap: wrap;
  margin: -6rpx;
}

.feature-item {
  width: calc(50% - 12rpx);
  margin: 6rpx;
  padding: 12rpx 8rpx;
  flex: 1 1 auto;
  min-height: 0;
  border-radius: 18rpx;
  background: #fcfdff;
  border: 2rpx solid #f2f6fb;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  transition: transform 0.15s ease;
}

.feature-item:active {
  transform: scale(0.97);
}

.feature-icon-wrap {
  width: 56rpx;
  height: 56rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 6rpx;
}

.feature-icon-text {
  font-size: 28rpx;
  line-height: 1;
}

.feature-label {
  font-size: 22rpx;
  font-weight: 600;
  color: #2a3441;
}

.feature-subtitle {
  font-size: 18rpx;
  color: #8e9dab;
  margin-top: 2rpx;
}
</style>

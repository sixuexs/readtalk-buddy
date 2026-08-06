<template>
  <view class="home-page">
    <view class="home-scroll">
      <!-- ===== Section 1: Radar Chart (5D) ===== -->
      <view class="section-card">
        <text class="section-title">能力雷达</text>
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
      </view>

      <!-- ===== Section 2: Line Chart (5D × 5 months) ===== -->
      <view class="section-card">
        <text class="section-title">趋势变化</text>
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

      <!-- ===== Section 3: Feature Grid (2×3) ===== -->
      <view class="section-card section-card--features">
        <text class="section-title">快捷功能</text>
        <view class="feature-grid">
          <view
            v-for="item in featureItems"
            :key="item.key"
            class="feature-item"
            @click="item.action"
          >
            <view class="feature-icon-wrap" :style="{ backgroundColor: item.bgColor, color: item.iconColor }">
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
import { ref, onMounted, onUnmounted, nextTick, getCurrentInstance } from 'vue'
import * as echarts from 'echarts'
import CustomTabBar from '@/components/CustomTabBar.vue'
import FloatingActionButton from '@/components/FloatingActionButton.vue'
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

// ── API base URL (matches simulation.ts) ──
const BASE_URL = 'http://localhost:8080'

// ── Radar indicator names & API field keys ──
const RADAR_INDICATORS = [
  { name: '清晰度', max: 100, key: 'avgClarity' },
  { name: '逻辑性', max: 100, key: 'avgLogicality' },
  { name: '共情倾听', max: 100, key: 'avgEmpathyListening' },
  { name: '互动性', max: 100, key: 'avgInteractivity' },
  { name: '松弛感', max: 100, key: 'avgRelaxation' },
] as const

const RADAR_FALLBACK: number[] = [60, 70, 65, 55, 75]

// ── Line chart mock data ──
const LINE_MONTHS = ['3月', '4月', '5月', '6月', '7月']
const LINE_SERIES_CONFIG = [
  { name: '清晰度', data: [55, 60, 62, 65, 70], color: '#3B9EFF' },
  { name: '逻辑性', data: [60, 58, 64, 68, 72], color: '#2ECFA0' },
  { name: '共情倾听', data: [50, 55, 58, 62, 65], color: '#FFAB40' },
  { name: '互动性', data: [45, 52, 55, 60, 63], color: '#FF7EB3' },
  { name: '松弛感', data: [65, 62, 60, 58, 60], color: '#A78BFA' },
]

// ── Feature grid items ──
interface FeatureItem {
  key: string
  label: string
  subtitle?: string
  iconText: string
  bgColor: string
  iconColor: string
  action: () => void
}

const featureItems: FeatureItem[] = [
  {
    key: 'promotion',
    label: '提升计划',
    subtitle: '即将上线',
    iconText: '↑',
    bgColor: '#FFF3E0',
    iconColor: '#FF8A5C',
    action: () => uni.showToast({ title: '提升计划即将上线', icon: 'none' }),
  },
  {
    key: 'relation',
    label: '关系图谱',
    iconText: '◈',
    bgColor: '#E8F4FF',
    iconColor: '#3B9EFF',
    action: () => uni.switchTab({ url: '/pages/relation/graph' }),
  },
  {
    key: 'simulation',
    label: '模拟训练',
    iconText: '▶',
    bgColor: '#E6FBF3',
    iconColor: '#2ECFA0',
    action: () => uni.switchTab({ url: '/pages/simulation/simulation' }),
  },
  {
    key: 'social',
    label: '会话记录',
    iconText: '☰',
    bgColor: '#F3EEFF',
    iconColor: '#A78BFA',
    action: () => uni.switchTab({ url: '/pages/social/social' }),
  },
  {
    key: 'icebreak',
    label: '破冰分析',
    iconText: '✦',
    bgColor: '#FFE8F2',
    iconColor: '#FF7EB3',
    action: () => uni.navigateTo({ url: '/pages/icebreak/icebreak' }),
  },
  {
    key: 'namecard',
    label: '个人名片',
    iconText: '◉',
    bgColor: '#FFFBF0',
    iconColor: '#FFAB40',
    action: () => uni.navigateTo({ url: '/pages/namecard/namecard' }),
  },
]

// ── Fetch user profile for radar data ──
async function fetchRadarData(): Promise<number[]> {
  return new Promise((resolve) => {
    uni.request({
      url: `${BASE_URL}/api/user/profile?userId=1`,
      method: 'GET',
      success: (res: any) => {
        const data = res.data
        if (data && typeof data === 'object') {
          const values = RADAR_INDICATORS.map((ind) => {
            const v = (data as Record<string, unknown>)[ind.key]
            return typeof v === 'number' ? v : 0
          })
          if (values.some((v) => v > 0)) {
            resolve(values)
            return
          }
        }
        resolve(RADAR_FALLBACK)
      },
      fail: () => {
        resolve(RADAR_FALLBACK)
      },
    })
  })
}

// ── Build echarts radar option ──
function buildRadarOption(radarValues: number[]) {
  return {
    radar: {
      indicator: RADAR_INDICATORS.map((ind) => ({ name: ind.name, max: ind.max })),
      center: ['50%', '55%'],
      radius: '62%',
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

// ── Build echarts line option ──
function buildLineOption() {
  return {
    tooltip: { trigger: 'axis' },
    legend: {
      data: LINE_SERIES_CONFIG.map((s) => s.name),
      bottom: 0,
      textStyle: { fontSize: 10, color: '#5C6B7A' },
      itemWidth: 16,
      itemHeight: 8,
    },
    grid: { top: 12, right: 16, bottom: 48, left: 40 },
    xAxis: {
      type: 'category',
      data: LINE_MONTHS,
      axisLine: { lineStyle: { color: '#E8ECF4' } },
      axisTick: { show: false },
      axisLabel: { color: '#8E9DAB' },
    },
    yAxis: {
      type: 'value',
      min: 40,
      max: 80,
      splitLine: { lineStyle: { color: '#F0E4F0', type: 'dashed' as const } },
      axisLabel: { color: '#8E9DAB' },
    },
    series: LINE_SERIES_CONFIG.map((s) => ({
      name: s.name,
      type: 'line',
      data: s.data,
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { color: s.color, width: 2 },
      itemStyle: { color: s.color },
    })),
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
  const radarValues = await fetchRadarData()
  const radarOption = buildRadarOption(radarValues)
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

// ── Lifecycle ──
onMounted(() => {
  // Delay slightly to allow DOM/layout to settle (especially for mini-program canvas)
  setTimeout(() => {
    initCharts()
  }, 120)
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
  background: #FFF6FA;
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

/* ========== Section Card ========== */
.section-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  width: 100%;
  box-sizing: border-box;
  background: #FFFFFF;
  border-radius: 20rpx;
  padding: 20rpx 16rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 4rpx 20rpx rgba(59, 158, 255, 0.06);
}

.section-card--features {
  flex: 1.6;
  min-height: 0;
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #2A3441;
  margin-bottom: 12rpx;
  display: block;
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

/* ========== Feature Grid (2×3) ========== */
.feature-grid {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-wrap: wrap;
  align-content: stretch;
  margin: -8rpx;
}

.feature-item {
  width: calc(50% - 16rpx);
  margin: 8rpx;
  padding: 12rpx 8rpx;
  flex: 1 1 auto;
  min-height: 0;
  border-radius: 20rpx;
  background: #FFFBFE;
  border: 2rpx solid #F5F0FA;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.feature-item:active {
  transform: scale(0.97);
  box-shadow: 0 4rpx 16rpx rgba(59, 158, 255, 0.12);
}

.feature-icon-wrap {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 6rpx;
}

.feature-icon-text {
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1;
}

.feature-label {
  font-size: 22rpx;
  font-weight: 500;
  color: #2A3441;
}

.feature-subtitle {
  font-size: 18rpx;
  color: #8E9DAB;
  margin-top: 2rpx;
}
</style>

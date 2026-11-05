<template>
  <view class="trend-chart">
    <scroll-view scroll-x class="tc-chips-scroll">
      <view class="tc-chips">
        <view class="tc-chip" :class="{ 'tc-chip--active': source === 'total' }" @tap="pick('total')">
          <text class="tc-chip-text">总分</text>
        </view>
        <view
          v-for="s in SUB_SKILLS"
          :key="s.key"
          class="tc-chip"
          :class="{ 'tc-chip--active': source === s.key, 'tc-chip--dim': !hasData(s.key) }"
          @tap="pick(s.key)"
        >
          <text class="tc-chip-text">{{ s.cnName }}</text>
        </view>
      </view>
    </scroll-view>

    <view class="tc-body">
      <view v-if="loading" class="tc-state"><text class="tc-state-text">加载趋势…</text></view>
      <view v-else-if="points.length < 2" class="tc-state">
        <text class="tc-state-text">{{ source === 'total' ? '完成更多训练后解锁趋势' : '该子技能样本不足，先针对性练几场' }}</text>
      </view>
      <template v-else>
        <!-- #ifdef H5 -->
        <view :id="domId" class="tc-surface" />
        <!-- #endif -->
        <!-- #ifndef H5 -->
        <canvas :id="canvasId" type="2d" class="tc-surface" />
        <!-- #endif -->
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { getCurrentInstance, nextTick, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { AbilityProfile } from '@/types/user'
import { SUB_SKILLS } from '@/constants/subSkills'
import { getSubskillTrend } from '@/api/plan'
// #ifndef H5
import WxCanvas from '@/utils/wx-canvas'
// #endif

const props = defineProps<{ profile: AbilityProfile }>()

const instance = getCurrentInstance()
const uid = instance?.uid ?? Math.floor(Math.random() * 1e6)
const domId = `trend-chart-${uid}`
const canvasId = `trend-canvas-${uid}`

const source = ref<string>('total')
const loading = ref(false)
const points = ref<{ label: string; score: number }[]>([])
const trendCache = new Map<string, { label: string; score: number }[]>()
let chart: echarts.ECharts | null = null

function hasData(key: string): boolean {
  return (props.profile.subSkillSampleCount?.[key] ?? 0) >= 1
}

async function pick(key: string) {
  if (source.value === key) return
  source.value = key
  await refreshSeries()
}

async function refreshSeries() {
  if (source.value === 'total') {
    const history = [...(props.profile.scoreHistory ?? [])].sort(
      (a, b) => new Date(a.scoredAt).getTime() - new Date(b.scoredAt).getTime(),
    )
    points.value = history.map((h, i) => ({
      label: history.length > 5 ? `#${i + 1}` : h.theme.slice(0, 4),
      score: h.score,
    }))
    await draw()
    return
  }
  const cached = trendCache.get(source.value)
  if (cached) {
    points.value = cached
  } else {
    loading.value = true
    try {
      const res = await getSubskillTrend(source.value, 10)
      if (res.code === 0) {
        const list = (res.data.points ?? []).map((p, i) => ({
          label: `#${i + 1}`,
          score: p.score,
        }))
        trendCache.set(source.value, list)
        points.value = list
      }
    } catch {
      points.value = []
    } finally {
      loading.value = false
    }
  }
  await draw()
}

function buildOption() {
  return {
    grid: { top: 24, right: 16, bottom: 26, left: 36 },
    xAxis: {
      type: 'category',
      data: points.value.map((p) => p.label),
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
        name: '分',
        type: 'line',
        data: points.value.map((p) => p.score),
        smooth: true,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: '#3B9EFF', width: 2 },
        itemStyle: { color: '#3B9EFF' },
        areaStyle: { color: 'rgba(59, 158, 255, 0.08)' },
      },
    ],
  }
}

async function draw() {
  await nextTick()
  if (points.value.length < 2) return
  if (chart) {
    chart.setOption(buildOption(), true)
    return
  }
  let instance_: echarts.ECharts | null = null
  // #ifdef H5
  instance_ = await initH5()
  // #endif
  // #ifndef H5
  instance_ = await initMini()
  // #endif
  if (instance_) {
    chart = instance_
    instance_.setOption(buildOption(), true)
  }
}

async function initH5(): Promise<echarts.ECharts | null> {
  let el = document.getElementById(domId)
  if (!el) {
    await new Promise((r) => setTimeout(r, 120))
    el = document.getElementById(domId)
  }
  return el ? echarts.init(el) : null
}

// #ifndef H5
function initMini(): Promise<echarts.ECharts | null> {
  return new Promise((resolve) => {
    const proxy = instance?.proxy
    const query = proxy ? uni.createSelectorQuery().in(proxy) : uni.createSelectorQuery()
    query
      .select(`#${canvasId}`)
      .fields({ node: true, size: true }, () => {})
      .exec((res: any) => {
        const info = res?.[0]
        const node = info?.node
        if (!node) {
          resolve(null)
          return
        }
        const dpr = uni.getSystemInfoSync().pixelRatio || 2
        const w = info.width || 300
        const h = info.height || 300
        ;(node as any).width = w * dpr
        ;(node as any).height = h * dpr
        const ctx = node.getContext('2d')
        const wxCanvas = new WxCanvas(ctx, canvasId, true, node)
        const c = echarts.init(wxCanvas as any, null, { width: w, height: h, devicePixelRatio: dpr })
        wxCanvas.setChart(c)
        resolve(c)
      })
  })
}
// #endif

watch(
  () => props.profile.totalSessions,
  () => {
    trendCache.clear()
    refreshSeries()
  },
)

onUnmounted(() => {
  chart?.dispose()
  chart = null
})

// 初始渲染总分
refreshSeries()
</script>

<style scoped>
.trend-chart {
  display: flex;
  flex-direction: column;
}
.tc-chips-scroll {
  width: 100%;
  white-space: nowrap;
}
.tc-chips {
  display: flex;
  gap: 10rpx;
  padding: 4rpx 0 14rpx;
}
.tc-chip {
  flex-shrink: 0;
  padding: 8rpx 20rpx;
  border-radius: 26rpx;
  background: #f3f4f6;
  border: 2rpx solid transparent;
}
.tc-chip--active {
  background: #ebf0ff;
  border-color: #5b8def;
}
.tc-chip--dim {
  opacity: 0.45;
}
.tc-chip-text {
  font-size: 22rpx;
  color: #555;
  white-space: nowrap;
}
.tc-body {
  height: 380rpx;
  position: relative;
}
.tc-surface {
  width: 100%;
  height: 100%;
  display: block;
}
.tc-state {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.tc-state-text {
  font-size: 24rpx;
  color: #b0b8c4;
}
</style>

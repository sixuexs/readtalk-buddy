<template>
  <view class="drill">
    <!-- 雷达（H5 可点击扇区下钻；小程序走下方维度 chip） -->
    <view class="drill-radar-wrap">
      <!-- #ifdef H5 -->
      <view :id="radarDomId" class="drill-radar" />
      <!-- #endif -->
      <!-- #ifndef H5 -->
      <canvas :id="radarCanvasId" type="2d" class="drill-radar" />
      <!-- #endif -->
    </view>

    <!-- 维度 chips（跨端主交互，P1-8） -->
    <view class="dim-chips">
      <view
        v-for="d in DIM_ORDER"
        :key="d"
        class="dim-chip"
        :class="{ 'dim-chip--active': selectedDim === d }"
        @tap="selectDim(d)"
      >
        <text class="dim-chip-label">{{ DIM_LABELS[d] }}</text>
        <text class="dim-chip-score">{{ dimAvg(d) }}</text>
      </view>
    </view>

    <!-- 子技能条列表 -->
    <view class="sub-list">
      <view
        v-for="s in dimSubSkills"
        :key="s.key"
        class="sub-row"
        :class="{
          'sub-row--insufficient': !hasSample(s.key),
          'sub-row--active': selectedSub === s.key,
        }"
        @tap="selectSub(s.key)"
      >
        <text class="sub-name">{{ s.cnName }}</text>
        <view class="sub-bar">
          <view class="sub-bar-fill" :style="{ width: (avg(s.key) ?? 0) + '%' }" />
        </view>
        <text class="sub-score">{{ hasSample(s.key) ? avg(s.key) : '—' }}</text>
      </view>
      <view v-if="insufficientHint" class="sub-hint">
        <text class="sub-hint-text">{{ insufficientHint }}</text>
      </view>
    </view>

    <!-- 证据卡区 -->
    <view v-if="selectedSub" class="evidence-zone">
      <view class="evidence-title-row">
        <text class="evidence-title">「{{ subSkillCn(selectedSub) }}」的历史证据</text>
        <text class="evidence-close" @tap="selectedSub = ''">收起 ✕</text>
      </view>
      <view v-if="evLoading" class="ev-state">
        <text class="ev-state-text">加载证据…</text>
      </view>
      <view v-else-if="!evidenceList.length" class="ev-state">
        <text class="ev-state-text">暂无该子技能的证据记录</text>
      </view>
      <template v-else>
        <EvidenceCard v-for="(e, i) in evidenceList" :key="i" :item="e" />
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import type { AbilityProfile } from '@/types/user'
import type { EvidenceItem } from '@/types/simulation'
import { getSubSkillEvidence } from '@/api/user'
import {
  DIM_LABELS,
  DIM_ORDER,
  MIN_SAMPLES,
  subSkillCn,
  subSkillsByDimension,
  type DimKey,
} from '@/constants/subSkills'
import EvidenceCard from './EvidenceCard.vue'
// #ifndef H5
import WxCanvas from '@/utils/wx-canvas'
// #endif

const props = defineProps<{ profile: AbilityProfile }>()

const instance = getCurrentInstance()
const uid = instance?.uid ?? Math.floor(Math.random() * 1e6)
const radarDomId = `drill-radar-${uid}`
const radarCanvasId = `drill-radar-canvas-${uid}`

let radarChart: echarts.ECharts | null = null

/** 维度 → profile 均值字段 */
const DIM_FIELD: Record<DimKey, keyof AbilityProfile> = {
  clarity: 'avgClarity',
  logicality: 'avgLogicality',
  empathy_listening: 'avgEmpathyListening',
  interactivity: 'avgInteractivity',
  relaxation: 'avgRelaxation',
}
/** 雷达展示名 → DimKey（H5 click 事件反查） */
const LABEL_TO_DIM: Record<string, DimKey> = Object.fromEntries(
  DIM_ORDER.map((d) => [DIM_LABELS[d], d]),
) as Record<string, DimKey>

const selectedDim = ref<DimKey>('clarity')
const selectedSub = ref('')
const evidenceList = ref<EvidenceItem[]>([])
const evLoading = ref(false)

function dimAvg(d: DimKey): number {
  return Number(props.profile[DIM_FIELD[d]] ?? 0)
}

/** 默认选中最弱维度 */
function weakestDim(): DimKey {
  return DIM_ORDER.reduce((min, d) => (dimAvg(d) < dimAvg(min) ? d : min), DIM_ORDER[0])
}

const dimSubSkills = computed(() => subSkillsByDimension(selectedDim.value))

function avg(key: string): number | null {
  return props.profile.subSkillAverages?.[key] ?? null
}
function sample(key: string): number {
  return props.profile.subSkillSampleCount?.[key] ?? 0
}
function hasSample(key: string): boolean {
  return sample(key) >= MIN_SAMPLES
}

const insufficientHint = computed(() => {
  const list = dimSubSkills.value
  if (list.length && list.every((s) => !hasSample(s.key))) {
    return '本维度子技能样本不足，完成更多训练后解锁诊断'
  }
  return ''
})

function selectDim(d: DimKey) {
  if (selectedDim.value === d) return
  selectedDim.value = d
  selectedSub.value = ''
  evidenceList.value = []
}

function selectSub(key: string) {
  if (!hasSample(key)) {
    const need = MIN_SAMPLES - sample(key)
    uni.showToast({ title: `样本不足，再练 ${need} 场解锁`, icon: 'none' })
    return
  }
  if (selectedSub.value === key) {
    selectedSub.value = ''
    return
  }
  selectedSub.value = key
  loadEvidence(key)
}

async function loadEvidence(key: string) {
  evLoading.value = true
  evidenceList.value = []
  try {
    const res = await getSubSkillEvidence(key, 20)
    if (res.code === 0 && res.data) {
      evidenceList.value = res.data.items ?? []
    }
  } catch {
    /* 静默：证据缺失不阻塞诊断 */
  } finally {
    evLoading.value = false
  }
}

// ── 雷达 ──
function buildRadarOption() {
  return {
    radar: {
      indicator: DIM_ORDER.map((d) => ({ name: DIM_LABELS[d], max: 100 })),
      center: ['50%', '55%'],
      radius: '62%',
      axisName: { color: '#5C6B7A', fontSize: 11 },
      splitArea: { areaStyle: { color: ['rgba(59,158,255,0.03)', 'rgba(59,158,255,0.02)'] } },
    },
    series: [
      {
        type: 'radar',
        data: [{ value: DIM_ORDER.map(dimAvg), name: '当前能力' }],
        symbol: 'circle',
        symbolSize: 4,
        lineStyle: { color: '#3B9EFF', width: 2 },
        areaStyle: { color: 'rgba(59,158,255,0.12)' },
        itemStyle: { color: '#3B9EFF' },
      },
    ],
  }
}

async function initH5(): Promise<echarts.ECharts | null> {
  await nextTick()
  let el = document.getElementById(radarDomId)
  if (!el) {
    await new Promise((r) => setTimeout(r, 120))
    el = document.getElementById(radarDomId)
  }
  if (!el) return null
  return echarts.init(el)
}

// #ifndef H5
function initMini(): Promise<echarts.ECharts | null> {
  return new Promise((resolve) => {
    const proxy = instance?.proxy
    const query = proxy ? uni.createSelectorQuery().in(proxy) : uni.createSelectorQuery()
    query
      .select(`#${radarCanvasId}`)
      .fields({ node: true, size: true }, () => {})
      .exec((res: any) => {
        const info = res?.[0]
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
        const wxCanvas = new WxCanvas(ctx, radarCanvasId, true, canvasNode)
        const chart = echarts.init(wxCanvas as any, null, { width: w, height: h, devicePixelRatio: dpr })
        wxCanvas.setChart(chart)
        resolve(chart)
      })
  })
}
// #endif

onMounted(async () => {
  selectedDim.value = weakestDim()
  await new Promise((r) => setTimeout(r, 120))
  // #ifdef H5
  const chart = await initH5()
  if (chart) {
    chart.setOption(buildRadarOption())
    chart.on('click', (p: any) => {
      const dim = LABEL_TO_DIM[p?.name]
      if (dim) selectDim(dim)
    })
    radarChart = chart
  }
  // #endif
  // #ifndef H5
  const mini = await initMini()
  if (mini) {
    mini.setOption(buildRadarOption())
    radarChart = mini
  }
  // #endif
})

// profile 刷新（onShow 重拉）后同步雷达
watch(
  () => props.profile,
  () => {
    radarChart?.setOption(buildRadarOption(), true)
  },
)

onUnmounted(() => {
  radarChart?.dispose()
  radarChart = null
})
</script>

<style scoped>
.drill {
  display: flex;
  flex-direction: column;
}
.drill-radar-wrap {
  width: 100%;
  height: 420rpx;
  position: relative;
  overflow: hidden;
}
.drill-radar {
  width: 100%;
  height: 100%;
  display: block;
}
.dim-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin: 8rpx 0 20rpx;
}
.dim-chip {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  padding: 10rpx 22rpx;
  border-radius: 28rpx;
  background: #f3f4f6;
  border: 2rpx solid transparent;
}
.dim-chip--active {
  background: #ebf0ff;
  border-color: #5b8def;
}
.dim-chip-label {
  font-size: 24rpx;
  color: #555;
}
.dim-chip-score {
  font-size: 24rpx;
  font-weight: 600;
  color: #5b8def;
}
.sub-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.sub-row {
  display: flex;
  align-items: center;
  padding: 14rpx 18rpx;
  border-radius: 12rpx;
  background: #f8f8f8;
  border: 2rpx solid transparent;
}
.sub-row--active {
  background: #f2f6ff;
  border-color: #c7d8ff;
}
.sub-row--insufficient {
  opacity: 0.45;
}
.sub-name {
  width: 210rpx;
  font-size: 24rpx;
  color: #444;
  flex-shrink: 0;
}
.sub-bar {
  flex: 1;
  height: 12rpx;
  background: #ececec;
  border-radius: 6rpx;
  overflow: hidden;
  margin: 0 14rpx;
}
.sub-bar-fill {
  height: 100%;
  border-radius: 6rpx;
  background: linear-gradient(90deg, #93c5fd, #5b8def);
  transition: width 0.4s ease;
}
.sub-score {
  width: 52rpx;
  text-align: right;
  font-size: 24rpx;
  font-weight: 600;
  color: #333;
}
.sub-hint {
  padding: 8rpx 0;
}
.sub-hint-text {
  font-size: 22rpx;
  color: #b0b8c4;
}
.evidence-zone {
  margin-top: 20rpx;
}
.evidence-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.evidence-title {
  font-size: 25rpx;
  font-weight: 600;
  color: #333;
}
.evidence-close {
  font-size: 22rpx;
  color: #999;
}
.ev-state {
  padding: 30rpx 0;
  display: flex;
  justify-content: center;
}
.ev-state-text {
  font-size: 24rpx;
  color: #b0b8c4;
}
</style>

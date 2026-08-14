<template>
  <view
    class="relation-chart"
    :class="{ 'relation-chart--fill': fillParent }"
    :style="fillParent ? undefined : { height: chartHeightPx }"
  >
    <!-- #ifdef H5 -->
    <view :id="domId" class="relation-chart__surface" />
    <!-- #endif -->

    <!-- #ifndef H5 -->
    <canvas
      :id="canvasId"
      type="2d"
      class="relation-chart__surface"
      @touchstart="onCanvasTouch('touchStart', $event)"
      @touchmove="onCanvasTouch('touchMove', $event)"
      @touchend="onCanvasTouch('touchEnd', $event)"
    />
    <!-- #endif -->
  </view>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick, getCurrentInstance } from 'vue'
import * as echarts from 'echarts'
import type { RelationContact } from '@/types/relation'
import { buildRelationGraphOption } from '@/utils/relationGraph'
import { SELF_NODE_NAME } from '@/data/mockRelations'

// #ifndef H5
import WxCanvas from '@/utils/wx-canvas'
// #endif

const props = withDefaults(
  defineProps<{
    contacts: RelationContact[]
    selectedId?: string | null
    selfName?: string
    height?: string
    fillParent?: boolean
  }>(),
  {
    selectedId: null,
    selfName: SELF_NODE_NAME,
    height: '52vh',
    fillParent: false,
  },
)

const emit = defineEmits(['select'])

const chartHeightPx = ref(props.height)
const instance = getCurrentInstance()
const uid = instance?.uid ?? Math.floor(Math.random() * 1e6)
const domId = `relation-graph-${uid}`
const canvasId = `relation-canvas-${uid}`

let chart: echarts.ECharts | null = null
let wxCanvasInstance: InstanceType<typeof WxCanvas> | null = null
/** 布局计算用尺寸（选中高亮时不应随侧栏抖动重算） */
let layoutWidth = 300
let layoutHeight = 300
let resizeHandler: (() => void) | null = null
let resizeTimer: ReturnType<typeof setTimeout> | null = null

const MIN_CHART_SIZE = 280

function noop() {}

function onCanvasTouch(wxName: string, e: any) {
  if (wxCanvasInstance?.event?.[wxName]) {
    wxCanvasInstance.event[wxName](e)
  }
}

function createQuery() {
  const proxy = instance?.proxy
  return proxy ? uni.createSelectorQuery().in(proxy) : uni.createSelectorQuery()
}

function readSizeFromChart(): boolean {
  if (!chart) return false
  const w = chart.getWidth()
  const h = chart.getHeight()
  if (w >= MIN_CHART_SIZE && h >= MIN_CHART_SIZE) {
    layoutWidth = w
    layoutHeight = h
    return true
  }
  return false
}

function getChartSize(): Promise<{ width: number; height: number }> {
  if (readSizeFromChart()) {
    return Promise.resolve({ width: layoutWidth, height: layoutHeight })
  }

  return new Promise((resolve) => {
    const query = createQuery()
    query
      .select('.relation-chart')
      .boundingClientRect((rect) => {
        if (rect && !Array.isArray(rect) && rect.width && rect.height) {
          resolve({
            width: Math.max(rect.width, MIN_CHART_SIZE),
            height: Math.max(rect.height, MIN_CHART_SIZE),
          })
        } else {
          const sys = uni.getSystemInfoSync()
          const wide = sys.windowWidth >= 768
          resolve({
            width: Math.max(sys.windowWidth - (wide ? 420 : 32), MIN_CHART_SIZE),
            height: Math.max(
              wide ? sys.windowHeight - 200 : Math.min(sys.windowHeight * 0.52, 480),
              MIN_CHART_SIZE,
            ),
          })
        }
      })
      .exec()
  })
}

function resolveNodeId(params: {
  dataType?: string
  data?: unknown
  name?: string
}): string | null | undefined {
  if (params.dataType !== 'node') return undefined
  const data = params.data as { id?: string; name?: string } | undefined
  if (data?.id) return data.id
  if (params.name) {
    if (params.name === props.selfName) return 'me'
    const found = props.contacts.find((c) => c.name === params.name)
    return found?.id
  }
  return undefined
}

function bindChartEvents() {
  if (!chart) return
  chart.off('click')
  chart.on('click', (params) => {
    const id = resolveNodeId(params)
    if (id === undefined) return
    if (id === 'me' || id === null) {
      emit('select', null)
      return
    }
    emit('select', id)
  })
}

function buildOption() {
  return buildRelationGraphOption({
    selfName: props.selfName,
    contacts: props.contacts,
    width: layoutWidth,
    height: layoutHeight,
    selectedId: props.selectedId,
    fontSize: layoutWidth > 500 ? 13 : 11,
  })
}

/** 全量重建（初始化、尺寸变化、联系人变更） */
function rebuildGraph() {
  if (!chart) return
  chart.setOption(buildOption(), true)
  bindChartEvents()
}

/** 仅更新高亮样式，保持节点坐标与缩放不变 */
function updateSelection() {
  if (!chart) return
  chart.setOption(buildOption(), { replaceMerge: ['series'] })
}

async function initH5() {
  await nextTick()
  let el = document.getElementById(domId)
  if (!el) {
    await new Promise((r) => setTimeout(r, 120))
    el = document.getElementById(domId)
  }
  if (!el) return
  const size = await getChartSize()
  layoutWidth = size.width
  layoutHeight = size.height
  chart?.dispose()
  chart = echarts.init(el)
  rebuildGraph()
}

// #ifndef H5
async function initCanvas() {
  await nextTick()
  const size = await getChartSize()
  layoutWidth = size.width
  layoutHeight = size.height

  return new Promise<void>((resolve) => {
    const query = createQuery()
    query
      .select(`#${canvasId}`)
      .fields({ node: true, size: true }, () => {})
      .exec((res) => {
        const info = res?.[0] as {
          node?: {
            getContext: (type: string) => CanvasRenderingContext2D
            width?: number
            height?: number
          }
          width?: number
          height?: number
        }
        const canvasNode = info?.node
        if (!canvasNode) {
          resolve()
          return
        }
        const dpr = uni.getSystemInfoSync().pixelRatio || 2
        const w = info.width || size.width
        const h = info.height || size.height
        canvasNode.width = w * dpr
        canvasNode.height = h * dpr
        layoutWidth = w
        layoutHeight = h

        const ctx = canvasNode.getContext('2d')
        wxCanvasInstance = new WxCanvas(ctx, canvasId, true, canvasNode)

        chart?.dispose()
        chart = echarts.init(wxCanvasInstance as any, null, {
          width: w,
          height: h,
          devicePixelRatio: dpr,
        })
        wxCanvasInstance.setChart(chart)
        rebuildGraph()
        resolve()
      })
  })
}
// #endif

async function initChart() {
  // #ifdef H5
  await initH5()
  // #endif
  // #ifndef H5
  await initCanvas()
  // #endif
}

function handleResize() {
  if (!chart) return
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(async () => {
    const prevW = layoutWidth
    const prevH = layoutHeight
    const size = await getChartSize()
    if (!readSizeFromChart()) {
      layoutWidth = size.width
      layoutHeight = size.height
    }
    chart?.resize()
    if (
      Math.abs(layoutWidth - prevW) > 8 ||
      Math.abs(layoutHeight - prevH) > 8
    ) {
      rebuildGraph()
    }
  }, 160)
}

onMounted(() => {
  setTimeout(() => {
    initChart()
  }, 80)
  resizeHandler = handleResize
  uni.onWindowResize(resizeHandler)
})

onUnmounted(() => {
  if (resizeHandler) {
    uni.offWindowResize(resizeHandler)
  }
  if (resizeTimer) clearTimeout(resizeTimer)
  chart?.dispose()
  chart = null
})

watch(
  () => props.height,
  (h) => {
    if (!props.fillParent) chartHeightPx.value = h
  },
)

watch(
  () => props.contacts,
  () => {
    if (chart) rebuildGraph()
  },
  { deep: true },
)

watch(
  () => props.selectedId,
  () => {
    if (chart) updateSelection()
  },
)

watch(
  () => props.selfName,
  () => {
    if (chart) rebuildGraph()
  },
)

watch(
  () => props.fillParent,
  () => {
    setTimeout(() => handleResize(), 100)
  },
)

defineExpose({ resize: handleResize, refresh: initChart })
</script>

<style scoped>
.relation-chart {
  width: 100%;
  min-height: 360px;
  position: relative;
}

.relation-chart--fill {
  flex: 1;
  min-height: 360px;
  height: 100% !important;
}

.relation-chart--fill .relation-chart__surface {
  min-height: 360px;
}

.relation-chart__surface {
  width: 100%;
  height: 100%;
  min-height: 360px;
  display: block;
}
</style>

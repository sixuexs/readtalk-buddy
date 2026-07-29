<template>
  <view class="graph-page">
    <!-- 顶部：标题 + 视图切换 -->
    <view class="page-header">
      <text class="page-title">关系图谱</text>
      <view class="view-toggle">
        <view
          class="toggle-btn"
          :class="{ 'toggle-btn--active': viewMode === 'graph' }"
          @tap="switchView('graph')"
        >
          <text class="toggle-text" :class="{ 'toggle-text--active': viewMode === 'graph' }">图谱视图</text>
        </view>
        <view
          class="toggle-btn"
          :class="{ 'toggle-btn--active': viewMode === 'list' }"
          @tap="switchView('list')"
        >
          <text class="toggle-text" :class="{ 'toggle-text--active': viewMode === 'list' }">通讯录视图</text>
        </view>
      </view>
    </view>

    <!--
      图谱视图：canvas 仅在加载时绘制一次静态底图（扇区/圆环/中心节点）并导出图片，
      联系人节点是普通 view 组件叠在底图上——z-index 正常参与层叠（卡片盖得住），
      选中/灰化/切换动画由 CSS transition 驱动。
    -->
    <view v-show="viewMode === 'graph'" class="canvas-wrap">
      <canvas
        v-show="!bgReady"
        type="2d"
        id="graphCanvas"
        class="graph-canvas"
      />
      <image
        v-if="bgUrl"
        class="graph-canvas graph-bg"
        :src="bgUrl"
        @load="bgReady = true"
        @tap="onBlankTap"
      />

      <!-- 节点层 -->
      <view
        v-for="node in nodeViews"
        v-show="bgReady"
        :key="node.id"
        class="graph-node"
        :class="{
          'graph-node--selected': node.id === selectedId,
          'graph-node--dimmed': selectedId !== null && node.id !== selectedId,
        }"
        :style="{ left: node.x + 'px', top: node.y + 'px' }"
        @tap="onNodeTap(node)"
      >
        <view class="node-circle" :style="{ backgroundColor: node.color }">
          <image
            v-if="node.contact.avatarUrl"
            class="node-avatar"
            :src="node.contact.avatarUrl"
            mode="aspectFill"
          />
          <text v-else class="node-initial">{{ node.contact.name.charAt(0) }}</text>
        </view>
        <view
          v-if="node.warning && !node.warning.dismissed"
          class="node-badge"
          :style="{ backgroundColor: badgeColor(node) }"
        />
        <text class="node-name">{{ node.contact.name }}</text>
      </view>

      <view v-if="loadError" class="canvas-error">
        <text class="canvas-error-text">{{ loadError }}</text>
        <view class="canvas-retry" @tap="loadGraphData">
          <text class="canvas-retry-text">重试</text>
        </view>
      </view>
    </view>

    <!-- 通讯录视图 -->
    <view v-show="viewMode === 'list'" class="list-wrap">
      <ContactListView :contacts="contacts" :warnings="warnings" @select="onListSelect" />
    </view>

    <!-- 详情弹窗 -->
    <ContactDetail
      v-if="selectedContact"
      :visible="detailVisible"
      :contact="selectedContact"
      :warning="selectedWarning"
      @close="onDetailClose"
      @dismissed="onWarningDismissed"
      @resumed="onWarningResumed"
    />

    <CustomTabBar />
    <FloatingActionButton />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, getCurrentInstance } from 'vue'
import { onReady } from '@dcloudio/uni-app'
import CustomTabBar from '@/components/CustomTabBar.vue'
import FloatingActionButton from '@/components/FloatingActionButton.vue'
import ContactDetail from '@/components/ContactDetail.vue'
import ContactListView from '@/components/ContactListView.vue'
import { getRelationGraph } from '@/api/relation'
import type { GraphContact, GraphWarning } from '@/types/relationGraph'

/** 节点视图模型：x/y 为圆心的画布像素坐标 */
interface NodeView {
  id: number
  x: number
  y: number
  color: string
  contact: GraphContact
  warning: GraphWarning | null
}

// ==================== 状态 ====================

const viewMode = ref<'graph' | 'list'>('graph')
const contacts = ref<GraphContact[]>([])
const warnings = ref<GraphWarning[]>([])
const loadError = ref('')

const detailVisible = ref(false)
const selectedContact = ref<GraphContact | null>(null)
const selectedWarning = ref<GraphWarning | null>(null)

// 当前选中节点 id：选中放大高亮，其余缩小灰化（CSS transition 过渡）
const selectedId = ref<number | null>(null)

// 静态底图：canvas 一次性绘制后导出，导出成功且图片加载完成后隐藏画布
const bgUrl = ref('')
const bgReady = ref(false)

// 画布逻辑尺寸（px），节点坐标以此计算
const canvasW = ref(0)
const canvasH = ref(0)
let canvasNode: any = null

const instance = getCurrentInstance()

// ==================== 常量 ====================

// 扇区顺序（第一版按 relationType 分区；TODO: category 词表定稿后切 category）
const SECTORS = ['朋友', '同事', '家人', '同学', 'other']
const SECTOR_SPAN = (Math.PI * 2) / SECTORS.length // 72°
const START_ANGLE = -Math.PI / 2 // 从正上方开始

const LEVEL_COLORS: Record<string, string> = {
  YELLOW: '#FBBF24',
  ORANGE: '#F97316',
  RED: '#EF4444',
}

const SECTOR_FILLS = ['#F0F7FF', '#F0FDF4', '#FEFCE8', '#FDF2F8', '#F8FAFC']
const NODE_COLORS = ['#60A5FA', '#34D399', '#FBBF24', '#F472B6', '#A78BFA']

// ==================== 生命周期 ====================

onReady(() => {
  initCanvas()
  loadGraphData()
})

// ==================== 数据加载 ====================

async function loadGraphData() {
  loadError.value = ''
  try {
    const res = await getRelationGraph()
    if (res.code === 0 && res.data) {
      contacts.value = res.data.contacts || []
      warnings.value = res.data.warnings || []
    } else {
      loadError.value = '数据加载失败'
    }
  } catch (e) {
    loadError.value = '无法连接服务器'
  }
}

// ==================== 静态底图 ====================

function initCanvas() {
  const query = uni.createSelectorQuery().in(instance?.proxy as any)
  query
    .select('#graphCanvas')
    .fields({ node: true, size: true } as any, () => {})
    .exec((res) => {
      if (!res || !res[0] || !res[0].node) return
      const canvas = res[0].node
      const ctx = canvas.getContext('2d')

      const dpr = uni.getSystemInfoSync().pixelRatio || 1
      canvasW.value = res[0].width
      canvasH.value = res[0].height
      canvas.width = canvasW.value * dpr
      canvas.height = canvasH.value * dpr
      ctx.scale(dpr, dpr)

      canvasNode = canvas
      drawBackground(ctx)
      exportBackground()
    })
}

/** 底图与联系人数据无关：扇区淡色底、三环、分界线、标签、中心用户节点 */
function drawBackground(ctx: any) {
  const cx = canvasW.value / 2
  const cy = canvasH.value / 2
  // 外环留出扇区标签空间
  const r3 = Math.min(canvasW.value, canvasH.value) / 2 - 36
  const r2 = r3 * 0.68
  const r1 = r3 * 0.4

  ctx.clearRect(0, 0, canvasW.value, canvasH.value)

  // 1) 扇区淡色底
  for (let i = 0; i < SECTORS.length; i++) {
    const a0 = START_ANGLE + i * SECTOR_SPAN
    ctx.beginPath()
    ctx.moveTo(cx, cy)
    ctx.arc(cx, cy, r3, a0, a0 + SECTOR_SPAN)
    ctx.closePath()
    ctx.fillStyle = SECTOR_FILLS[i % SECTOR_FILLS.length]
    ctx.fill()
  }

  // 2) 三环同心圆
  ctx.strokeStyle = '#CBD5E1'
  ctx.lineWidth = 1
  ctx.setLineDash([4, 4])
  for (const r of [r1, r2, r3]) {
    ctx.beginPath()
    ctx.arc(cx, cy, r, 0, Math.PI * 2)
    ctx.stroke()
  }
  ctx.setLineDash([])

  // 环标注（沿正上方半径）
  ctx.fillStyle = '#94A3B8'
  ctx.font = '10px sans-serif'
  ctx.textAlign = 'left'
  ctx.textBaseline = 'middle'
  ctx.fillText('内环', cx + 4, cy - r1 + 10)
  ctx.fillText('二环', cx + 4, cy - r2 + 10)
  ctx.fillText('外环', cx + 4, cy - r3 + 10)

  // 3) 扇区分界线 + 标签
  ctx.strokeStyle = '#E2E8F0'
  ctx.lineWidth = 1
  for (let i = 0; i < SECTORS.length; i++) {
    const a = START_ANGLE + i * SECTOR_SPAN
    ctx.beginPath()
    ctx.moveTo(cx, cy)
    ctx.lineTo(cx + r3 * Math.cos(a), cy + r3 * Math.sin(a))
    ctx.stroke()

    // 标签画在外环外的扇区中线上
    const mid = a + SECTOR_SPAN / 2
    const lx = cx + (r3 + 20) * Math.cos(mid)
    const ly = cy + (r3 + 20) * Math.sin(mid)
    ctx.fillStyle = '#64748B'
    ctx.font = 'bold 12px sans-serif'
    ctx.textAlign = 'center'
    ctx.textBaseline = 'middle'
    ctx.fillText(SECTORS[i] === 'other' ? '其他' : SECTORS[i], lx, ly)
  }

  // 4) 中心用户节点
  ctx.beginPath()
  ctx.arc(cx, cy, 24, 0, Math.PI * 2)
  ctx.fillStyle = '#3B82F6'
  ctx.fill()
  ctx.strokeStyle = '#FFFFFF'
  ctx.lineWidth = 3
  ctx.stroke()
  ctx.fillStyle = '#FFFFFF'
  ctx.font = 'bold 14px sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText('你', cx, cy)
}

/** 底图导出为图片后隐藏画布；失败则保留画布兜底（节点层可能被原生画布盖住） */
function exportBackground() {
  if (!canvasNode) return
  uni.canvasToTempFilePath(
    {
      canvas: canvasNode,
      success: (res: any) => {
        bgUrl.value = res.tempFilePath
      },
    } as any,
    instance?.proxy as any
  )
}

// ==================== 节点布局 ====================

/** 环（intimacy）× 扇区（relationType）分组后沿弧线均匀分布 */
const nodeViews = computed<NodeView[]>(() => {
  if (!canvasW.value || !canvasH.value || !contacts.value.length) return []
  const cx = canvasW.value / 2
  const cy = canvasH.value / 2
  const r3 = Math.min(canvasW.value, canvasH.value) / 2 - 36
  const ringRadius = [r3 * 0.4, r3 * 0.68, r3]

  const warnMap = new Map<number, GraphWarning>()
  warnings.value.forEach((w) => warnMap.set(w.contactId, w))

  const groups = new Map<string, GraphContact[]>()
  for (const c of contacts.value) {
    const key = `${ringIndex(c.intimacyScore)}-${sectorIndex(c.relationType)}`
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(c)
  }

  const result: NodeView[] = []
  groups.forEach((members, key) => {
    const [ring, sector] = key.split('-').map(Number)
    const radius = ringRadius[ring]
    const a0 = START_ANGLE + sector * SECTOR_SPAN
    members.forEach((c, i) => {
      const angle = a0 + (SECTOR_SPAN * (i + 1)) / (members.length + 1)
      result.push({
        id: c.id,
        x: cx + radius * Math.cos(angle),
        y: cy + radius * Math.sin(angle),
        color: NODE_COLORS[c.id % NODE_COLORS.length],
        contact: c,
        warning: warnMap.get(c.id) || null,
      })
    })
  })
  return result
})

/** intimacy → 环序号：内环 ≥70 / 二环 40-69 / 外环 <40 */
function ringIndex(intimacy: number): number {
  if (intimacy >= 70) return 0
  if (intimacy >= 40) return 1
  return 2
}

function sectorIndex(relationType: string): number {
  const idx = SECTORS.indexOf(relationType)
  return idx === -1 ? SECTORS.length - 1 : idx // 未知类型归 other
}

function badgeColor(node: NodeView): string {
  return (node.warning && LEVEL_COLORS[node.warning.level]) || '#FBBF24'
}

// ==================== 交互 ====================

function onNodeTap(node: NodeView) {
  selectedId.value = node.id
  showContactDetail(node.contact, node.warning)
}

/** 点底图空白处：收起详情 + 取消选中态 */
function onBlankTap() {
  closeDetail()
}

function onListSelect(contact: GraphContact) {
  const warning = warnings.value.find((w) => w.contactId === contact.id) || null
  showContactDetail(contact, warning)
}

function showContactDetail(contact: GraphContact, warning: GraphWarning | null) {
  selectedContact.value = contact
  selectedWarning.value = warning
  detailVisible.value = true
}

function onDetailClose() {
  closeDetail()
}

function closeDetail() {
  detailVisible.value = false
  selectedId.value = null
}

function switchView(mode: 'graph' | 'list') {
  viewMode.value = mode
  // 切回图谱时若底图尚未生成则补初始化
  if (mode === 'graph' && !bgUrl.value) initCanvas()
}

/** 暂不提醒 / 挽救后：预警打 dismissed 标记（角标消失，卡片保留"继续提醒"） */
function onWarningDismissed(contactId: number) {
  setWarningDismissed(contactId, true)
}

/** 继续提醒：清除 dismissed 标记，角标重新出现 */
function onWarningResumed(contactId: number) {
  setWarningDismissed(contactId, false)
}

function setWarningDismissed(contactId: number, dismissed: boolean) {
  warnings.value = warnings.value.map((w) =>
    w.contactId === contactId ? { ...w, dismissed } : w
  )
  if (selectedWarning.value && selectedWarning.value.contactId === contactId) {
    selectedWarning.value = { ...selectedWarning.value, dismissed }
  }
  // 节点角标由 nodeViews computed 自动联动，无需手动重绘
}
</script>

<style scoped>
.graph-page {
  min-height: 100vh;
  background-color: #F8FAFC;
  padding-bottom: 140rpx;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 32rpx;
}

.page-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #1F2937;
}

.view-toggle {
  display: flex;
  background-color: #E2E8F0;
  border-radius: 32rpx;
  padding: 4rpx;
}

.toggle-btn {
  padding: 10rpx 28rpx;
  border-radius: 28rpx;
}

.toggle-btn--active {
  background-color: #FFFFFF;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.08);
}

.toggle-text {
  font-size: 24rpx;
  color: #64748B;
}

.toggle-text--active {
  color: #3B82F6;
  font-weight: 500;
}

.canvas-wrap {
  position: relative;
  margin: 0 24rpx;
  background-color: #FFFFFF;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.graph-canvas {
  width: 100%;
  height: 850rpx;
}

/* 静态底图：与画布同尺寸原位替换 */
.graph-bg {
  display: block;
}

/* ==================== 节点层（普通组件，CSS 过渡动画） ==================== */

.graph-node {
  position: absolute;
  width: 40px;
  height: 40px;
  margin-left: -20px;
  margin-top: -20px;
  z-index: 10;
  transition: transform 0.2s ease-out;
}

.node-circle {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  border: 2px solid #FFFFFF;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  transition: filter 0.2s ease-out, opacity 0.2s ease-out,
    border-color 0.2s ease-out, box-shadow 0.2s ease-out;
}

.node-avatar {
  width: 100%;
  height: 100%;
}

.node-initial {
  font-size: 14px;
  font-weight: bold;
  color: #FFFFFF;
}

.node-badge {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #FFFFFF;
  box-sizing: border-box;
  z-index: 2;
  transition: filter 0.2s ease-out, opacity 0.2s ease-out;
}

.node-name {
  position: absolute;
  top: 44px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  color: #334155;
  white-space: nowrap;
  transition: color 0.2s ease-out;
}

/* 选中：放大 + 蓝色描边光环 */
.graph-node--selected {
  transform: scale(1.25);
  z-index: 20;
}

.graph-node--selected .node-circle {
  border-color: #3B82F6;
  box-shadow: 0 0 0 5px rgba(59, 130, 246, 0.18);
}

/* 未选中：缩小 + 灰化 */
.graph-node--dimmed {
  transform: scale(0.75);
  z-index: 5;
}

.graph-node--dimmed .node-circle {
  filter: grayscale(1);
  opacity: 0.55;
}

.graph-node--dimmed .node-badge {
  filter: grayscale(1);
  opacity: 0.5;
}

.graph-node--dimmed .node-name {
  color: #B0BCCB;
}

.canvas-error {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(255, 255, 255, 0.92);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.canvas-error-text {
  font-size: 26rpx;
  color: #9CA3AF;
  margin-bottom: 20rpx;
}

.canvas-retry {
  padding: 12rpx 48rpx;
  border-radius: 32rpx;
  background-color: #3B82F6;
}

.canvas-retry-text {
  font-size: 26rpx;
  color: #FFFFFF;
}

.list-wrap {
  flex: 1;
  padding-top: 8rpx;
}
</style>

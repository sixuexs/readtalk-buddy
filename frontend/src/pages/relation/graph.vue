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

      <!-- 溢出「+N」芯片：组满折叠，点按弹出全员名单；圆点色=隐藏成员最高预警级 -->
      <view
        v-for="chip in overflowChips"
        :key="chip.key"
        v-show="bgReady"
        class="overflow-chip"
        :style="{ left: chip.x + 'px', top: chip.y + 'px' }"
        @tap.stop="openGroupPopup(chip.key)"
      >
        <text class="overflow-chip-text">+{{ chip.count }}</text>
        <view
          v-if="chip.dotColor"
          class="overflow-chip-dot"
          :style="{ backgroundColor: chip.dotColor }"
        />
      </view>

      <view v-if="loadError" class="canvas-error">
        <text class="canvas-error-text">{{ loadError }}</text>
        <view class="canvas-retry" @tap="loadGraphData">
          <text class="canvas-retry-text">重试</text>
        </view>
      </view>

      <!-- 图例：预警圆点配色说明（红橙已拉开色相/明度差） -->
      <view class="graph-legend">
        <view class="legend-item">
          <view class="legend-dot" :style="{ backgroundColor: LEVEL_COLORS.RED }" />
          <text class="legend-text">滑落预警</text>
        </view>
        <view class="legend-item">
          <view class="legend-dot" :style="{ backgroundColor: LEVEL_COLORS.ORANGE }" />
          <text class="legend-text">沉寂预警</text>
        </view>
      </view>
    </view>

    <!-- 通讯录视图（左滑可删除关系） -->
    <view v-show="viewMode === 'list'" class="list-wrap">
      <ContactListView
        :contacts="contacts"
        :warnings="warnings"
        @select="onListSelect"
        @delete="onContactDelete"
      />
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

    <!-- 分组成员浮层：某环×扇区人数过多时，点图谱「+N」芯片展开查看全员 -->
    <view v-if="popupGroup" class="group-popup-mask" @tap="closeGroupPopup">
      <view class="group-popup" @tap.stop>
        <view class="group-popup-head">
          <text class="group-popup-title">{{ popupGroup.sectorName }} · {{ popupGroup.ringName }}</text>
          <text class="group-popup-count">共 {{ popupGroup.members.length }} 人</text>
          <view class="group-popup-close" @tap="closeGroupPopup">
            <text class="group-popup-close-text">✕</text>
          </view>
        </view>
        <scroll-view class="group-popup-list" scroll-y>
          <view
            v-for="(m, idx) in popupGroup.members"
            :key="m.id"
            class="group-popup-item"
            @tap="onPopupMemberTap(m)"
          >
            <text class="gp-rank">{{ idx + 1 }}</text>
            <view class="gp-avatar" :style="{ backgroundColor: popupAvatarBg(m.id) }">
              <text class="gp-avatar-text">{{ m.name.charAt(0) }}</text>
              <view
                v-if="activeWarning(m.id)"
                class="gp-badge"
                :style="{ backgroundColor: popupDotColor(m.id) }"
              />
            </view>
            <view class="gp-info">
              <text class="gp-name">{{ m.name }}</text>
              <text v-if="activeWarning(m.id)" class="gp-warn-text">{{ popupWarnLabel(m.id) }}</text>
            </view>
            <view class="gp-right">
              <text class="gp-intimacy">{{ Math.round(m.intimacyScore) }}</text>
              <text class="gp-intimacy-label">亲密度</text>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

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
import { getRelationGraph, deleteContact } from '@/api/relation'
import { LEVEL_COLORS, WARN_TYPE_LABELS, levelRank } from '@/constants/warningLevel'
import type { GraphContact, GraphWarning, WarningLevel } from '@/types/relationGraph'

/** 节点视图模型：x/y 为圆心的画布像素坐标 */
interface NodeView {
  id: string
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
const selectedId = ref<string | null>(null)

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

const RING_NAMES = ['内环', '二环', '外环']

const SECTOR_FILLS = ['#F0F7FF', '#F0FDF4', '#FEFCE8', '#FDF2F8', '#F8FAFC']
const NODE_COLORS = ['#60A5FA', '#34D399', '#FBBF24', '#F472B6', '#A78BFA']

/** string id → 稳定色板下标（替代原来的 number 取模） */
function hashIndex(id: string, mod: number): number {
  let h = 0
  for (let i = 0; i < id.length; i++) {
    h = (h * 31 + id.charCodeAt(i)) >>> 0
  }
  return h % mod
}

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

/** 同组节点沿弧线的最小间距（px）：40px 节点本体 + 6px 间隙 */
const NODE_SPACING = 46

/** 溢出芯片视图模型：某组人数超容量时显示 "+N"，点按弹成员浮层 */
interface OverflowChip {
  key: string
  x: number
  y: number
  count: number
  /** 被隐藏成员中最高等级未冷却预警的圆点色；null = 无预警不画点 */
  dotColor: string | null
}

/** 成员分组信息（"+N" 浮层展示用） */
interface GroupInfo {
  key: string
  ringName: string
  sectorName: string
  /** 组内全员，按亲密度降序 */
  members: GraphContact[]
}

/**
 * 环（intimacy）× 扇区（relationType）分组后沿弧线均匀分布。
 * 每组容量按所在环弧长计算；超容量时按亲密度降序保留前 N 人，
 * 其余折叠进末位槽的 "+N" 芯片（芯片圆点 = 被隐藏者最高预警级）。
 */
const graphLayout = computed(() => {
  const nodes: NodeView[] = []
  const chips: OverflowChip[] = []
  const groupMap = new Map<string, GroupInfo>()
  if (!canvasW.value || !canvasH.value || !contacts.value.length) {
    return { nodes, chips, groupMap }
  }
  const cx = canvasW.value / 2
  const cy = canvasH.value / 2
  const r3 = Math.min(canvasW.value, canvasH.value) / 2 - 36
  const ringRadius = [r3 * 0.4, r3 * 0.68, r3]

  const warnMap = new Map<string, GraphWarning>()
  warnings.value.forEach((w) => warnMap.set(w.contactId, w))

  const rawGroups = new Map<string, { ring: number; sector: number; members: GraphContact[] }>()
  for (const c of contacts.value) {
    const ring = ringIndex(c.intimacyScore)
    const sector = sectorIndex(c.relationType)
    const key = `${ring}-${sector}`
    let g = rawGroups.get(key)
    if (!g) {
      g = { ring, sector, members: [] }
      rawGroups.set(key, g)
    }
    g.members.push(c)
  }

  rawGroups.forEach((g, key) => {
    const radius = ringRadius[g.ring]
    const a0 = START_ANGLE + g.sector * SECTOR_SPAN
    const sorted = [...g.members].sort((x, y) => y.intimacyScore - x.intimacyScore)
    const capacity = Math.max(1, Math.floor((radius * SECTOR_SPAN) / NODE_SPACING))
    const visible = sorted.slice(0, capacity)
    const hidden = sorted.slice(capacity)
    // 槽位数 = 可见人数（+ 溢出芯片占 1 槽），均匀分布时给芯片留出末位
    const slotCount = hidden.length > 0 ? visible.length + 1 : visible.length

    visible.forEach((c, i) => {
      const angle = a0 + (SECTOR_SPAN * (i + 1)) / (slotCount + 1)
      nodes.push({
        id: c.id,
        x: cx + radius * Math.cos(angle),
        y: cy + radius * Math.sin(angle),
        color: NODE_COLORS[hashIndex(c.id, NODE_COLORS.length)],
        contact: c,
        warning: warnMap.get(c.id) || null,
      })
    })

    if (hidden.length > 0) {
      const angle = a0 + (SECTOR_SPAN * slotCount) / (slotCount + 1)
      chips.push({
        key,
        x: cx + radius * Math.cos(angle),
        y: cy + radius * Math.sin(angle),
        count: hidden.length,
        dotColor: worstWarningColor(hidden, warnMap),
      })
    }

    groupMap.set(key, {
      key,
      ringName: RING_NAMES[g.ring],
      sectorName: SECTORS[g.sector] === 'other' ? '其他' : SECTORS[g.sector],
      members: sorted,
    })
  })

  return { nodes, chips, groupMap }
})

const nodeViews = computed(() => graphLayout.value.nodes)
const overflowChips = computed(() => graphLayout.value.chips)

/** 一组联系人里最高等级且未冷却的预警颜色；无则 null */
function worstWarningColor(list: GraphContact[], warnMap: Map<string, GraphWarning>): string | null {
  let best: WarningLevel | null = null
  for (const c of list) {
    const w = warnMap.get(c.id)
    if (w && !w.dismissed && (!best || levelRank(w.level) > levelRank(best))) {
      best = w.level
    }
  }
  return best ? LEVEL_COLORS[best] : null
}

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
function onWarningDismissed(contactId: string) {
  setWarningDismissed(contactId, true)
}

/** 继续提醒：清除 dismissed 标记，角标重新出现 */
function onWarningResumed(contactId: string) {
  setWarningDismissed(contactId, false)
}

function setWarningDismissed(contactId: string, dismissed: boolean) {
  warnings.value = warnings.value.map((w) =>
    w.contactId === contactId ? { ...w, dismissed } : w
  )
  if (selectedWarning.value && selectedWarning.value.contactId === contactId) {
    selectedWarning.value = { ...selectedWarning.value, dismissed }
  }
  // 节点角标由 nodeViews computed 自动联动，无需手动重绘
}

// ==================== 分组成员浮层 ====================

const popupGroupKey = ref('')
const popupGroup = computed<GroupInfo | null>(() =>
  popupGroupKey.value ? graphLayout.value.groupMap.get(popupGroupKey.value) ?? null : null,
)

function openGroupPopup(key: string) {
  popupGroupKey.value = key
}

function closeGroupPopup() {
  popupGroupKey.value = ''
}

/** 浮层中点成员：收起浮层并打开详情（与通讯录选择同路径） */
function onPopupMemberTap(contact: GraphContact) {
  closeGroupPopup()
  const warning = warnings.value.find((w) => w.contactId === contact.id) || null
  showContactDetail(contact, warning)
}

/** 该联系人的生效预警（非冷却），无则 null */
function activeWarning(contactId: string): GraphWarning | null {
  return warnings.value.find((w) => w.contactId === contactId && !w.dismissed) || null
}

function popupDotColor(contactId: string): string {
  const w = activeWarning(contactId)
  return w ? LEVEL_COLORS[w.level] || '#FBBF24' : 'transparent'
}

function popupWarnLabel(contactId: string): string {
  const w = activeWarning(contactId)
  return w ? `${WARN_TYPE_LABELS[w.type]}预警` : ''
}

function popupAvatarBg(id: string): string {
  return NODE_COLORS[hashIndex(id, NODE_COLORS.length)]
}

// ==================== 删除关系（通讯录视图） ====================

/**
 * 左滑删除确认后调后端删除，成功即本地同步 contacts/warnings。
 * 图谱视图节点由 graphLayout computed 自动重算，无需额外刷新。
 */
function onContactDelete(contact: GraphContact) {
  uni.showModal({
    title: '删除关系',
    content: `确定删除「${contact.name}」吗？删除后图谱与预警中将移除该书友`,
    confirmColor: '#DC2626',
    success: async (res) => {
      if (!res.confirm) return
      try {
        const result = await deleteContact(contact.id)
        if (result.code === 0) {
          contacts.value = contacts.value.filter((c) => c.id !== contact.id)
          warnings.value = warnings.value.filter((w) => w.contactId !== contact.id)
          if (selectedContact.value && selectedContact.value.id === contact.id) {
            closeDetail()
          }
          uni.showToast({ title: '已删除', icon: 'none' })
        } else {
          uni.showToast({ title: '删除失败，请重试', icon: 'none' })
        }
      } catch {
        uni.showToast({ title: '网络异常，删除失败', icon: 'none' })
      }
    },
  })
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

/* ==================== 溢出「+N」芯片 ==================== */

.overflow-chip {
  position: absolute;
  width: 40px;
  height: 40px;
  margin-left: -20px;
  margin-top: -20px;
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.95);
  border: 1.5px dashed #94A3B8;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 11;
  box-shadow: 0 2rpx 8rpx rgba(15, 23, 42, 0.12);
  transition: transform 0.15s ease;
}

.overflow-chip:active {
  transform: scale(0.9);
}

.overflow-chip-text {
  font-size: 14px;
  font-weight: 700;
  color: #475569;
}

.overflow-chip-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid #FFFFFF;
  box-sizing: border-box;
  z-index: 2;
}

/* ==================== 图例 ==================== */

.graph-legend {
  position: absolute;
  left: 16rpx;
  bottom: 12rpx;
  display: flex;
  align-items: center;
  z-index: 15;
}

.legend-item {
  display: flex;
  align-items: center;
  margin-right: 28rpx;
}

.legend-dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  margin-right: 8rpx;
}

.legend-text {
  font-size: 20rpx;
  color: #64748B;
}

/* ==================== 分组成员浮层 ==================== */

.group-popup-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background-color: rgba(15, 23, 42, 0.45);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
}

.group-popup {
  width: 100%;
  max-height: 70vh;
  background-color: #FFFFFF;
  border-radius: 32rpx 32rpx 0 0;
  display: flex;
  flex-direction: column;
  padding-bottom: 24rpx;
  box-sizing: border-box;
}

.group-popup-head {
  position: relative;
  display: flex;
  align-items: center;
  padding: 28rpx 100rpx 20rpx 32rpx;
  border-bottom: 1rpx solid #F1F5F9;
}

.group-popup-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2937;
  margin-right: 16rpx;
}

.group-popup-count {
  font-size: 24rpx;
  color: #9CA3AF;
}

.group-popup-close {
  position: absolute;
  right: 20rpx;
  top: 16rpx;
  padding: 12rpx;
}

.group-popup-close-text {
  font-size: 32rpx;
  color: #94A3B8;
}

.group-popup-list {
  max-height: 56vh;
}

.group-popup-item {
  display: flex;
  align-items: center;
  padding: 20rpx 32rpx;
}

.group-popup-item:active {
  background-color: #F8FAFC;
}

.gp-rank {
  width: 44rpx;
  flex-shrink: 0;
  font-size: 24rpx;
  color: #94A3B8;
}

.gp-avatar {
  position: relative;
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}

.gp-avatar-text {
  font-size: 30rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.gp-badge {
  position: absolute;
  top: -2rpx;
  right: -2rpx;
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  border: 3rpx solid #FFFFFF;
  box-sizing: border-box;
}

.gp-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.gp-name {
  font-size: 28rpx;
  font-weight: 500;
  color: #1F2937;
}

.gp-warn-text {
  font-size: 22rpx;
  color: #B45309;
  margin-top: 4rpx;
}

.gp-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-left: 16rpx;
}

.gp-intimacy {
  font-size: 30rpx;
  font-weight: 600;
  color: #3B82F6;
}

.gp-intimacy-label {
  font-size: 20rpx;
  color: #9CA3AF;
}

.list-wrap {
  flex: 1;
  padding-top: 8rpx;
}
</style>

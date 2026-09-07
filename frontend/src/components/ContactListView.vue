<template>
  <scroll-view class="contact-list" scroll-y>
    <view v-if="!contacts.length" class="list-empty">
      <text class="list-empty-text">暂无联系人</text>
    </view>

    <!-- 条目支持左滑露出删除按钮（与社交记录页同构） -->
    <view
      v-for="contact in contacts"
      :key="contact.id"
      class="swipe-cell"
    >
      <!-- 底层删除按钮（左滑露出） -->
      <view class="swipe-delete" @click.stop="onTapDelete(contact)">
        <text class="swipe-delete-text">删除</text>
      </view>

      <!-- 内容层：跟手平移 -->
      <view
        class="swipe-content"
        :class="{ 'swipe-content--dragging': draggingId === contact.id }"
        :style="{ transform: `translateX(${offsetOf(contact.id)}px)` }"
        @touchstart="onTouchStart(contact.id, $event)"
        @touchmove="onTouchMove(contact.id, $event)"
        @touchend="onTouchEnd(contact.id)"
        @touchcancel="onTouchEnd(contact.id)"
        @click="onItemTap(contact)"
      >
        <view class="item-avatar" :style="{ backgroundColor: avatarBg(contact.id) }">
          <image
            v-if="contact.avatarUrl"
            class="item-avatar-img"
            :src="contact.avatarUrl"
            mode="aspectFill"
          />
          <text v-else class="item-avatar-text">{{ contact.name.charAt(0) }}</text>
          <!-- 预警角标 -->
          <view
            v-if="warningOf(contact.id)"
            class="item-badge"
            :style="{ backgroundColor: badgeColor(contact.id) }"
          />
        </view>

        <view class="item-info">
          <text class="item-name">{{ contact.name }}</text>
          <text class="item-relation">{{ contact.relationType }}</text>
        </view>

        <view class="item-right">
          <text class="item-intimacy">{{ Math.round(contact.intimacyScore) }}</text>
          <text class="item-intimacy-label">亲密度</text>
        </view>
      </view>
    </view>
  </scroll-view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { GraphContact, GraphWarning } from '@/types/relationGraph'
import { LEVEL_COLORS } from '@/constants/warningLevel'

const props = defineProps<{
  contacts: GraphContact[]
  warnings: GraphWarning[]
}>()

const emit = defineEmits<{
  (e: 'select', contact: GraphContact): void
  (e: 'delete', contact: GraphContact): void
}>()

const AVATAR_COLORS = ['#93C5FD', '#A7F3D0', '#FDE68A', '#FCA5A5', '#C4B5FD']

function avatarBg(id: string): string {
  return AVATAR_COLORS[hashIndex(id, AVATAR_COLORS.length)]
}

/** string id → 稳定色板下标 */
function hashIndex(id: string, mod: number): number {
  let h = 0
  for (let i = 0; i < id.length; i++) {
    h = (h * 31 + id.charCodeAt(i)) >>> 0
  }
  return h % mod
}

function warningOf(contactId: string): GraphWarning | undefined {
  // 冷却中（已"暂不提醒"）不显示角标
  return props.warnings.find((w) => w.contactId === contactId && !w.dismissed)
}

function badgeColor(contactId: string): string {
  const w = warningOf(contactId)
  return w ? LEVEL_COLORS[w.level] || '#FBBF24' : 'transparent'
}

// ===== 左滑删除手势（与 social.vue 已验证实现同构，按 contact.id 键控） =====
/** 删除按钮宽度（px），与样式 .swipe-delete 的 144rpx 对应 */
const DELETE_W = 70

// 当前左滑展开的条目（同一时刻最多一条）
const openId = ref('')
// 正在拖动的条目与实时偏移（拖动中关闭过渡动画，跟手）
const draggingId = ref('')
const dragOffset = ref(0)

let startX = 0
let startY = 0
let startOffset = 0
// null=未判定；true=水平滑动（接管）；false=垂直滑动（交给列表滚动）
let directionLocked: boolean | null = null
// 水平滑动后抑制本次点击，避免左滑误触打开详情
let clickSuppressed = false

function offsetOf(id: string): number {
  if (draggingId.value === id) return dragOffset.value
  if (openId.value === id) return -DELETE_W
  return 0
}

function onTouchStart(id: string, e: TouchEvent) {
  const touch = e.touches?.[0]
  if (!touch) return
  startX = touch.clientX
  startY = touch.clientY
  startOffset = offsetOf(id)
  dragOffset.value = startOffset
  draggingId.value = id
  directionLocked = null
  clickSuppressed = false
  // 滑动新条目时收起已展开的其他条目
  if (openId.value && openId.value !== id) {
    openId.value = ''
  }
}

function onTouchMove(id: string, e: TouchEvent) {
  if (draggingId.value !== id) return
  const touch = e.touches?.[0]
  if (!touch) return
  const dx = touch.clientX - startX
  const dy = touch.clientY - startY

  // 位移超过阈值后锁定方向：水平则接管，垂直则放行给列表滚动
  if (directionLocked === null) {
    if (Math.abs(dx) < 8 && Math.abs(dy) < 8) return
    directionLocked = Math.abs(dx) > Math.abs(dy)
    if (directionLocked) clickSuppressed = true
  }
  if (!directionLocked) return

  // 只允许向左滑出删除按钮，右滑最多回到 0
  dragOffset.value = Math.min(0, Math.max(-DELETE_W, startOffset + dx))
}

function onTouchEnd(id: string) {
  if (draggingId.value !== id) return
  // 越过一半吸附为展开，否则收起
  if (directionLocked) {
    openId.value = dragOffset.value < -DELETE_W / 2 ? id : ''
  }
  draggingId.value = ''
  directionLocked = null
}

function onItemTap(contact: GraphContact) {
  if (clickSuppressed) {
    clickSuppressed = false
    return
  }
  // 展开状态下点击内容先收起，不打开详情
  if (openId.value === contact.id) {
    openId.value = ''
    return
  }
  emit('select', contact)
}

function onTapDelete(contact: GraphContact) {
  emit('delete', contact)
}
</script>

<style scoped>
.contact-list {
  height: 100%;
}

.list-empty {
  padding: 120rpx 0;
  display: flex;
  justify-content: center;
}

.list-empty-text {
  font-size: 26rpx;
  color: #9CA3AF;
}

/* 滑动单元格：外层裁剪，删除按钮垫在底层 */
.swipe-cell {
  position: relative;
  margin: 0 24rpx 20rpx;
  border-radius: 20rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.swipe-delete {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 140rpx;
  background: #ef4444;
  display: flex;
  align-items: center;
  justify-content: center;
}

.swipe-delete-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #fff;
}

/* 内容层：默认带回弹过渡，拖动中关闭以跟手 */
.swipe-content {
  position: relative;
  z-index: 1;
  background: #fff;
  display: flex;
  align-items: center;
  padding: 24rpx;
  transition: transform 0.2s ease;
}

.swipe-content--dragging {
  transition: none;
}

.item-avatar {
  position: relative;
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}

.item-avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.item-avatar-text {
  font-size: 36rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.item-badge {
  position: absolute;
  top: 0;
  right: 0;
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  border: 4rpx solid #FFFFFF;
  box-sizing: border-box;
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.item-name {
  font-size: 30rpx;
  font-weight: 500;
  color: #1F2937;
  margin-bottom: 6rpx;
}

.item-relation {
  font-size: 24rpx;
  color: #9CA3AF;
}

.item-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.item-intimacy {
  font-size: 32rpx;
  font-weight: 600;
  color: #3B82F6;
}

.item-intimacy-label {
  font-size: 20rpx;
  color: #9CA3AF;
}
</style>

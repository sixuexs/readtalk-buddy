<template>
  <view v-if="visible" class="detail-popup" :style="{ height: sheetHeight + 'px' }">
    <!-- 拖拽横条：上下拉动调整卡片高度 -->
    <view
      class="detail-drag"
      @touchstart="onDragStart"
      @touchmove.stop.prevent="onDragMove"
    >
      <view class="detail-drag-bar" />
    </view>

    <!-- 头部：头像 + 名字 + 关系类型 -->
      <view class="detail-head">
        <view class="detail-avatar" :style="{ backgroundColor: avatarBg }">
          <image
            v-if="contact.avatarUrl"
            class="detail-avatar-img"
            :src="contact.avatarUrl"
            mode="aspectFill"
          />
          <text v-else class="detail-avatar-text">{{ contact.name.charAt(0) }}</text>
        </view>
        <view class="detail-head-info">
          <text class="detail-name">{{ contact.name }}</text>
          <text class="detail-relation">{{ contact.relationType }}</text>
        </view>
        <view class="detail-close" @tap="handleClose">
          <text class="detail-close-text">×</text>
        </view>
      </view>

      <scroll-view class="detail-body" scroll-y>
        <!-- 基本信息 -->
        <view class="info-grid">
          <view class="info-item">
            <text class="info-label">亲密度</text>
            <text class="info-value">{{ Math.round(contact.intimacyScore) }}/100</text>
          </view>
          <view class="info-item">
            <text class="info-label">上次联系</text>
            <text class="info-value">{{ lastContactText }}</text>
          </view>
        </view>

        <view v-if="contact.personality" class="info-row">
          <text class="info-label">性格</text>
          <text class="info-value">{{ contact.personality }}</text>
        </view>

        <view v-if="contact.interests.length" class="info-row">
          <text class="info-label">兴趣爱好</text>
          <view class="tag-list">
            <view v-for="(tag, i) in contact.interests" :key="'i' + i" class="tag tag--blue">
              <text class="tag-text tag-text--blue">{{ tag }}</text>
            </view>
          </view>
        </view>

        <view v-if="contact.labels.length" class="info-row">
          <text class="info-label">身份标签</text>
          <view class="tag-list">
            <view v-for="(tag, i) in contact.labels" :key="'l' + i" class="tag tag--green">
              <text class="tag-text tag-text--green">{{ tag }}</text>
            </view>
          </view>
        </view>

        <!-- 预警卡片 -->
        <view v-if="warning" class="detail-warning">
          <WarningCard
            :warning="warning"
            :dismissed="warning.dismissed"
            @view-advice="loadAdvice('light')"
            @recover="handleRecover"
            @dismiss="handleDismiss"
            @resume="handleResume"
          />
        </view>

        <!-- 建议面板 -->
        <view v-if="adviceVisible" class="detail-advice">
          <AdvicePanel :advice="advice" :mode="adviceMode" :loading="adviceLoading" />
        </view>
      </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import WarningCard from '@/components/WarningCard.vue'
import AdvicePanel from '@/components/AdvicePanel.vue'
import { getContactAdvice, dismissWarning, resumeWarning } from '@/api/relation'
import type { GraphContact, GraphWarning, AdviceResult, AdviceMode } from '@/types/relationGraph'

const props = defineProps<{
  visible: boolean
  contact: GraphContact
  warning: GraphWarning | null
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'dismissed', contactId: number): void
  (e: 'resumed', contactId: number): void
}>()

// 建议面板状态
const adviceVisible = ref(false)
const adviceLoading = ref(false)
const adviceMode = ref<AdviceMode>('light')
const advice = ref<AdviceResult | null>(null)

// ==================== 卡片高度拖拽 ====================

const WIN_H = uni.getSystemInfoSync().windowHeight
const SHEET_MIN = 220 // 最小高度：保留头部 + 拖拽条
const SHEET_MAX = Math.round(WIN_H * 0.85)
const SHEET_DEFAULT = Math.round(WIN_H * 0.45)

const sheetHeight = ref(SHEET_DEFAULT)
let dragStartY = 0
let dragStartH = 0

function onDragStart(e: any) {
  const touch = e.touches && e.touches[0]
  if (!touch) return
  dragStartY = touch.clientY
  dragStartH = sheetHeight.value
}

function onDragMove(e: any) {
  const touch = e.touches && e.touches[0]
  if (!touch) return
  // 上拉增高、下拉降低，限制在 min~max 之间
  const dy = dragStartY - touch.clientY
  sheetHeight.value = Math.min(SHEET_MAX, Math.max(SHEET_MIN, dragStartH + dy))
}

// 每次打开重置为默认高度
watch(
  () => props.visible,
  (v) => {
    if (v) sheetHeight.value = SHEET_DEFAULT
  }
)

// 切换联系人时重置建议面板
watch(
  () => props.contact.id,
  () => {
    adviceVisible.value = false
    advice.value = null
  }
)

const AVATAR_COLORS = ['#93C5FD', '#A7F3D0', '#FDE68A', '#FCA5A5', '#C4B5FD']

const avatarBg = computed(() => AVATAR_COLORS[props.contact.id % AVATAR_COLORS.length])

const lastContactText = computed(() => {
  if (!props.contact.lastContactTime) return '暂无记录'
  const last = new Date(props.contact.lastContactTime.replace(/-/g, '/').replace('T', ' '))
  const days = Math.max(0, Math.floor((Date.now() - last.getTime()) / 86400000))
  if (days === 0) return '今天'
  return `${days} 天前`
})

// 查看个性化建议（light）/ 挽救方案（full）
async function loadAdvice(mode: AdviceMode) {
  adviceMode.value = mode
  adviceVisible.value = true
  adviceLoading.value = true
  advice.value = null
  try {
    const res = await getContactAdvice(props.contact.id, mode)
    if (res.code === 0) {
      advice.value = res.data
    } else {
      uni.showToast({ title: '建议生成失败', icon: 'none' })
    }
  } catch (e) {
    uni.showToast({ title: '网络错误，请重试', icon: 'none' })
  } finally {
    adviceLoading.value = false
  }
}

// 挽救方案：拉 full 建议 + dismiss（冷却预警，避免重复打扰）
async function handleRecover() {
  await loadAdvice('full')
  try {
    await dismissWarning(props.contact.id)
    emit('dismissed', props.contact.id)
  } catch (e) {
    // 静默失败：方案已展示，冷却失败不打断
  }
}

// 暂不提醒：卡片保留，动作区切换为"继续提醒"（dismissed 状态由父级 warning 下发）
async function handleDismiss() {
  try {
    const res = await dismissWarning(props.contact.id)
    if (res.code !== 0) throw new Error('dismiss failed')
    uni.showToast({ title: '7 天内不再提醒', icon: 'none' })
    emit('dismissed', props.contact.id)
  } catch (e) {
    uni.showToast({ title: '操作失败，请重试', icon: 'none' })
  }
}

// 继续提醒：取消冷却，恢复三按钮状态与图谱角标
async function handleResume() {
  try {
    const res = await resumeWarning(props.contact.id)
    if (res.code !== 0) throw new Error('resume failed')
    uni.showToast({ title: '已恢复提醒', icon: 'none' })
    emit('resumed', props.contact.id)
  } catch (e) {
    uni.showToast({ title: '操作失败，请重试', icon: 'none' })
  }
}

function handleClose() {
  adviceVisible.value = false
  advice.value = null
  emit('close')
}
</script>

<style scoped>
/* 无遮罩底部弹层：叠在图谱之上，高度由拖拽横条控制（inline style 下发） */
.detail-popup {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  background-color: #FFFFFF;
  border-radius: 32rpx 32rpx 0 0;
  padding: 0 32rpx 32rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 -8rpx 32rpx rgba(15, 23, 42, 0.12);
}

/* 拖拽区：加大触摸热区，中间一条小横条 */
.detail-drag {
  flex-shrink: 0;
  padding: 16rpx 0 12rpx;
  display: flex;
  justify-content: center;
}

.detail-drag-bar {
  width: 72rpx;
  height: 8rpx;
  border-radius: 4rpx;
  background-color: #D1D5DB;
}

.detail-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  margin-bottom: 24rpx;
}

.detail-avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  margin-right: 20rpx;
}

.detail-avatar-img {
  width: 100%;
  height: 100%;
}

.detail-avatar-text {
  font-size: 40rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.detail-head-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.detail-name {
  font-size: 34rpx;
  font-weight: 600;
  color: #1F2937;
  margin-bottom: 4rpx;
}

.detail-relation {
  font-size: 24rpx;
  color: #6B7280;
}

.detail-close {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background-color: #F3F4F6;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-close-text {
  font-size: 36rpx;
  color: #6B7280;
  line-height: 1;
}

.detail-body {
  /* 填满弹层剩余高度，内部滚动 */
  flex: 1;
  height: 0;
}

.info-grid {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.info-grid .info-item {
  flex: 1;
  background-color: #F9FAFB;
  border-radius: 16rpx;
  padding: 20rpx;
  display: flex;
  flex-direction: column;
}

.info-row {
  margin-bottom: 20rpx;
  display: flex;
  flex-direction: column;
}

.info-label {
  font-size: 22rpx;
  color: #9CA3AF;
  margin-bottom: 8rpx;
}

.info-value {
  font-size: 28rpx;
  color: #1F2937;
  font-weight: 500;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag {
  border-radius: 24rpx;
  padding: 6rpx 20rpx;
}

.tag--blue {
  background-color: #EFF6FF;
}

.tag--green {
  background-color: #ECFDF5;
}

.tag-text {
  font-size: 24rpx;
}

.tag-text--blue {
  color: #2563EB;
}

.tag-text--green {
  color: #059669;
}

.detail-warning {
  margin-bottom: 20rpx;
}

.detail-advice {
  margin-bottom: 20rpx;
}
</style>

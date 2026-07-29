<template>
  <view class="warning-card" :class="levelClass">
    <view class="warning-head">
      <view class="warning-dot" :style="{ backgroundColor: levelColor }" />
      <text class="warning-type">{{ typeText }}</text>
      <text class="warning-level" :style="{ color: levelColor }">{{ levelText }}</text>
    </view>
    <text class="warning-reason">{{ warning.reason }}</text>
    <text class="warning-action-hint">{{ actionHint }}</text>
    <!-- 已暂停：只保留"继续提醒"，点击后恢复三按钮状态 -->
    <view v-if="dismissed" class="warning-actions">
      <view class="action-btn action-btn--resume" @tap="$emit('resume')">
        <text class="action-btn-text action-btn-text--resume">继续提醒</text>
      </view>
    </view>
    <view v-else class="warning-actions">
      <view class="action-btn action-btn--primary" @tap="$emit('view-advice')">
        <text class="action-btn-text action-btn-text--primary">查看个性化建议</text>
      </view>
      <view class="action-btn action-btn--recover" @tap="$emit('recover')">
        <text class="action-btn-text action-btn-text--recover">挽救方案</text>
      </view>
      <view class="action-btn" @tap="$emit('dismiss')">
        <text class="action-btn-text">暂不提醒</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { GraphWarning } from '@/types/relationGraph'

const props = defineProps<{
  warning: GraphWarning
  /** 已选择"暂不提醒"：动作区只显示"继续提醒" */
  dismissed?: boolean
}>()

defineEmits<{
  (e: 'view-advice'): void
  (e: 'recover'): void
  (e: 'dismiss'): void
  (e: 'resume'): void
}>()

// 预警级别 → 角标色（与图谱节点角标一致）
const LEVEL_COLORS: Record<string, string> = {
  YELLOW: '#FBBF24',
  ORANGE: '#F97316',
  RED: '#EF4444',
}

const levelColor = computed(() => LEVEL_COLORS[props.warning.level] || '#FBBF24')

const typeText = computed(() =>
  props.warning.type === 'DECAY' ? '滑落预警' : '沉寂预警'
)

const levelText = computed(() => {
  switch (props.warning.level) {
    case 'RED': return '严重'
    case 'ORANGE': return '中等'
    default: return '轻微'
  }
})

const actionHint = computed(() => {
  if (props.dismissed) return '已暂停提醒，7 天内不再打扰'
  return props.warning.type === 'DECAY'
    ? '建议行动：尽快主动联系，用共同话题重建热度'
    : '建议行动：发一条自然的问候，打破沉寂'
})

const levelClass = computed(() => `warning-card--${props.warning.level.toLowerCase()}`)
</script>

<style scoped>
.warning-card {
  border-radius: 20rpx;
  padding: 24rpx;
  background-color: #FFF7ED;
  border: 2rpx solid #FED7AA;
  display: flex;
  flex-direction: column;
}

.warning-card--red {
  background-color: #FEF2F2;
  border-color: #FECACA;
}

.warning-card--yellow {
  background-color: #FEFCE8;
  border-color: #FDE68A;
}

.warning-head {
  display: flex;
  align-items: center;
  margin-bottom: 12rpx;
}

.warning-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  margin-right: 12rpx;
}

.warning-type {
  font-size: 28rpx;
  font-weight: 600;
  color: #333333;
  margin-right: 12rpx;
}

.warning-level {
  font-size: 24rpx;
  font-weight: 500;
}

.warning-reason {
  font-size: 26rpx;
  color: #666666;
  margin-bottom: 8rpx;
}

.warning-action-hint {
  font-size: 24rpx;
  color: #999999;
  margin-bottom: 20rpx;
}

.warning-actions {
  display: flex;
  gap: 16rpx;
}

.action-btn {
  flex: 1;
  height: 64rpx;
  border-radius: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #F3F4F6;
}

.action-btn--primary {
  background-color: #3B82F6;
}

.action-btn--recover {
  background-color: #FFEDD5;
}

.action-btn--resume {
  background-color: #DBEAFE;
}

.action-btn-text {
  font-size: 24rpx;
  color: #666666;
}

.action-btn-text--primary {
  color: #FFFFFF;
}

.action-btn-text--recover {
  color: #EA580C;
}

.action-btn-text--resume {
  color: #2563EB;
  font-weight: 500;
}
</style>

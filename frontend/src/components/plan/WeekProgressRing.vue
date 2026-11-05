<template>
  <view class="ring-card">
    <view class="ring-main">
      <view class="ring-count">
        <text class="ring-done">{{ done }}</text>
        <text class="ring-total">/ {{ total }}</text>
      </view>
      <text class="ring-label">本周处方任务</text>
    </view>
    <view class="ring-bar-wrap">
      <view class="ring-bar" :style="{ width: rate + '%' }" />
    </view>
    <view class="ring-streak">
      <text class="ring-streak-text">🔥 连续 {{ streakDays }} 天</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ done: number; total: number; streakDays?: number }>()

const rate = computed(() => (props.total === 0 ? 0 : Math.round((props.done / props.total) * 100)))
</script>

<style scoped>
.ring-card {
  margin: 0 24rpx 20rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  padding: 24rpx 28rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}
.ring-main {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
}
.ring-count {
  display: flex;
  align-items: baseline;
}
.ring-done {
  font-size: 48rpx;
  font-weight: bold;
  color: #5b8def;
}
.ring-total {
  font-size: 26rpx;
  color: #999;
}
.ring-label {
  font-size: 24rpx;
  color: #666;
}
.ring-bar-wrap {
  height: 14rpx;
  background: #f0f0f0;
  border-radius: 7rpx;
  overflow: hidden;
}
.ring-bar {
  height: 100%;
  border-radius: 7rpx;
  background: linear-gradient(90deg, #93c5fd, #5b8def);
  transition: width 0.4s ease;
}
.ring-streak {
  display: flex;
}
.ring-streak-text {
  font-size: 22rpx;
  color: #f59e0b;
}
</style>

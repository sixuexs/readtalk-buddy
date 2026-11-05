<template>
  <view class="rec-card">
    <view class="rec-head">
      <text class="rec-icon">🎯</text>
      <view class="rec-title-wrap">
        <text class="rec-title">今日靶向训练：{{ rec.cnName }}</text>
        <text v-if="rec.summary" class="rec-summary">{{ rec.summary }}</text>
      </view>
    </view>
    <view class="rec-meta">
      <text class="rec-tag">{{ rec.theme }}</text>
      <text class="rec-tag rec-tag--alt">{{ rec.personality }}</text>
      <text v-if="rec.taskId" class="rec-task">↩ 关联处方任务</text>
    </view>
    <view class="rec-btn" @tap="start">
      <text class="rec-btn-text">开始靶向训练</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { PlanRecommendation } from '@/types/plan'
import { setSimFocus } from '@/store/simFocus'

const props = defineProps<{ rec: PlanRecommendation }>()

function start() {
  setSimFocus({
    focusSubSkill: props.rec.subSkill,
    cnName: props.rec.cnName,
    theme: props.rec.theme,
    personality: props.rec.personality,
    prescriptionTaskId: props.rec.taskId,
  })
  uni.switchTab({ url: '/pages/simulation/simulation' })
}
</script>

<style scoped>
.rec-card {
  margin: 0 24rpx 20rpx;
  background: linear-gradient(135deg, #eef4ff, #f6f0ff);
  border-radius: 16rpx;
  border: 2rpx solid #dbe6ff;
  padding: 24rpx 28rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.rec-head {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
}
.rec-icon {
  font-size: 40rpx;
}
.rec-title-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.rec-title {
  font-size: 29rpx;
  font-weight: 600;
  color: #2b4a8f;
}
.rec-summary {
  font-size: 23rpx;
  color: #5a6b8c;
  line-height: 1.5;
}
.rec-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10rpx;
}
.rec-tag {
  font-size: 22rpx;
  color: #3b6fd4;
  background: #ffffffcc;
  padding: 4rpx 16rpx;
  border-radius: 18rpx;
}
.rec-tag--alt {
  color: #7c3aed;
}
.rec-task {
  font-size: 20rpx;
  color: #999;
}
.rec-btn {
  height: 76rpx;
  border-radius: 38rpx;
  background: linear-gradient(135deg, #5b8def, #3b6fd4);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 14rpx rgba(59, 111, 212, 0.3);
}
.rec-btn:active {
  opacity: 0.85;
}
.rec-btn-text {
  color: #fff;
  font-size: 27rpx;
  font-weight: 600;
}
</style>

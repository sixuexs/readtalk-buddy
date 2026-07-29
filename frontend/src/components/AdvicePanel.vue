<template>
  <view class="advice-panel">
    <!-- 加载中 -->
    <view v-if="loading" class="advice-loading">
      <text class="advice-loading-text">AI 正在生成建议…</text>
    </view>

    <template v-else-if="advice">
      <view class="advice-title-row">
        <text class="advice-title">{{ mode === 'full' ? '挽救方案' : '个性化建议' }}</text>
      </view>

      <!-- 切入话题 -->
      <view v-if="advice.entryTopics.length" class="advice-section">
        <text class="section-label">切入话题</text>
        <view v-for="(topic, i) in advice.entryTopics" :key="'t' + i" class="section-item">
          <text class="section-item-dot">·</text>
          <text class="section-item-text">{{ topic }}</text>
        </view>
      </view>

      <!-- 开场白 -->
      <view v-if="advice.openingLine" class="advice-section">
        <text class="section-label">开场白</text>
        <view class="opening-line">
          <text class="opening-line-text">“{{ advice.openingLine }}”</text>
        </view>
      </view>

      <!-- 注意事项 -->
      <view v-if="advice.cautions.length" class="advice-section">
        <text class="section-label">注意事项</text>
        <view v-for="(c, i) in advice.cautions" :key="'c' + i" class="section-item">
          <text class="section-item-dot">·</text>
          <text class="section-item-text">{{ c }}</text>
        </view>
      </view>

      <!-- full 模式：挽救步骤 + 预期效果 -->
      <template v-if="mode === 'full'">
        <view v-if="advice.recoverSteps.length" class="advice-section">
          <text class="section-label">挽救步骤</text>
          <view v-for="(s, i) in advice.recoverSteps" :key="'s' + i" class="section-item">
            <text class="section-item-step">{{ i + 1 }}</text>
            <text class="section-item-text">{{ s }}</text>
          </view>
        </view>

        <view v-if="advice.expectation" class="advice-section">
          <text class="section-label">预期效果</text>
          <text class="expectation-text">{{ advice.expectation }}</text>
        </view>
      </template>
    </template>
  </view>
</template>

<script setup lang="ts">
import type { AdviceResult, AdviceMode } from '@/types/relationGraph'

defineProps<{
  advice: AdviceResult | null
  mode: AdviceMode
  loading: boolean
}>()
</script>

<style scoped>
.advice-panel {
  border-radius: 20rpx;
  padding: 24rpx;
  background-color: #F0F9FF;
  border: 2rpx solid #BAE6FD;
  display: flex;
  flex-direction: column;
}

.advice-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx 0;
}

.advice-loading-text {
  font-size: 26rpx;
  color: #0284C7;
}

.advice-title-row {
  margin-bottom: 16rpx;
}

.advice-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #0369A1;
}

.advice-section {
  margin-bottom: 20rpx;
  display: flex;
  flex-direction: column;
}

.advice-section:last-child {
  margin-bottom: 0;
}

.section-label {
  font-size: 24rpx;
  font-weight: 600;
  color: #0284C7;
  margin-bottom: 8rpx;
}

.section-item {
  display: flex;
  align-items: flex-start;
  margin-bottom: 6rpx;
}

.section-item-dot {
  font-size: 26rpx;
  color: #0284C7;
  margin-right: 8rpx;
  line-height: 1.5;
}

.section-item-step {
  min-width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background-color: #0284C7;
  color: #FFFFFF;
  font-size: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12rpx;
  margin-top: 4rpx;
}

.section-item-text {
  flex: 1;
  font-size: 26rpx;
  color: #334155;
  line-height: 1.5;
}

.opening-line {
  background-color: #FFFFFF;
  border-radius: 12rpx;
  padding: 16rpx;
}

.opening-line-text {
  font-size: 26rpx;
  color: #0369A1;
  line-height: 1.5;
}

.expectation-text {
  font-size: 26rpx;
  color: #334155;
  line-height: 1.5;
}
</style>

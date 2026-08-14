<template>
  <view class="page-container">
    <scroll-view class="result-scroll" scroll-y>
      <!-- 顶部：锦囊妙计（矩形圆角框） -->
      <view class="banner">
        <text class="banner-text">锦囊妙计</text>
      </view>

      <!-- 开场白建议（3 条） -->
      <view class="result-card">
        <text class="result-card-label">开场白建议</text>
        <view class="opening-list">
          <view v-for="(o, idx) in analysis.openings" :key="idx" class="opening-item">
            <view class="opening-num">
              <text class="opening-num-text">{{ idx + 1 }}</text>
            </view>
            <text class="opening-text">{{ o }}</text>
          </view>
          <view v-if="!analysis.openings.length" class="result-empty">
            <text class="result-empty-text">暂无开场白建议</text>
          </view>
        </view>
      </view>

      <!-- 话题建议 -->
      <view class="result-card">
        <text class="result-card-label">话题建议</text>
        <view class="result-list">
          <view v-for="(t, idx) in analysis.topics" :key="idx" class="result-list-item">
            <text class="result-list-num">{{ idx + 1 }}.</text>
            <text class="result-list-text">{{ t }}</text>
          </view>
          <view v-if="!analysis.topics.length" class="result-empty">
            <text class="result-empty-text">暂无话题建议</text>
          </view>
        </view>
      </view>

      <!-- 避雷指南 -->
      <view class="result-card">
        <text class="result-card-label">避雷指南</text>
        <view class="result-list">
          <view v-for="(w, idx) in analysis.warnings" :key="idx" class="result-list-item">
            <text class="result-list-dot">●</text>
            <text class="result-list-text">{{ w }}</text>
          </view>
          <view v-if="!analysis.warnings.length" class="result-empty">
            <text class="result-empty-text">暂无避雷提醒</text>
          </view>
        </view>
      </view>

      <!-- 重新生成妙计 -->
      <view class="reset-wrap">
        <view class="reset-btn" @tap="handleRegenerate">
          <text class="reset-btn-text">重新生成妙计</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { icebreakResultStore } from '@/store/icebreak'
import type { IceBreakAnalysis } from '@/types/simulation'

// 从跨页缓存读取结果
const analysis = computed<IceBreakAnalysis>(
  () =>
    icebreakResultStore.analysis ?? {
      openings: [],
      topics: [],
      warnings: [],
    },
)

function handleRegenerate() {
  uni.navigateBack()
}
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}

.result-scroll {
  flex: 1;
  min-height: 0;
  padding-bottom: 60rpx;
}

/* 顶部横幅：矩形圆角框 */
.banner {
  margin: 24rpx 30rpx;
  height: 110rpx;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(59, 130, 246, 0.3);
}

.banner-text {
  font-size: 42rpx;
  font-weight: 700;
  color: #fff;
  letter-spacing: 10rpx;
}

/* 结果卡片 */
.result-card {
  margin: 0 30rpx 20rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.result-card-label {
  font-size: 28rpx;
  font-weight: 600;
  color: #e63d3d;
  display: block;
  margin-bottom: 16rpx;
}

/* 开场白 */
.opening-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.opening-item {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  background: #f8f8f8;
  border-radius: 12rpx;
  padding: 20rpx;
}

.opening-num {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: #2563eb;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.opening-num-text {
  font-size: 24rpx;
  color: #fff;
  font-weight: 600;
}

.opening-text {
  flex: 1;
  font-size: 28rpx;
  color: #444;
  line-height: 1.6;
}

/* 列表 */
.result-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.result-list-item {
  display: flex;
  align-items: flex-start;
  gap: 10rpx;
}

.result-list-num {
  font-size: 26rpx;
  font-weight: 600;
  color: #2563eb;
  min-width: 36rpx;
  line-height: 1.6;
  flex-shrink: 0;
}

.result-list-dot {
  font-size: 22rpx;
  color: #e63d3d;
  line-height: 1.6;
  flex-shrink: 0;
}

.result-list-text {
  font-size: 26rpx;
  color: #555;
  line-height: 1.6;
  flex: 1;
}

.result-empty {
  padding: 20rpx 0;
  text-align: center;
}

.result-empty-text {
  font-size: 24rpx;
  color: #bbb;
}

/* 重新生成按钮 */
.reset-wrap {
  padding: 20rpx 30rpx 60rpx;
}

.reset-btn {
  width: 100%;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 48rpx;
  background: linear-gradient(135deg, #2dd4bf, #14b8a6);
  box-shadow: 0 6rpx 20rpx rgba(20, 184, 166, 0.35);
}

.reset-btn-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
  letter-spacing: 4rpx;
}
</style>

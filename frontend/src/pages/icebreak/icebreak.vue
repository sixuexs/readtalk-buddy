<template>
  <view class="page-container">
    <!-- 顶部导航栏 -->
    <view class="top-bar">
      <text class="top-title">破冰分析</text>
    </view>

    <!-- ==================== 表单态 ==================== -->
    <scroll-view v-if="viewMode === 'form'" class="form-scroll" scroll-y>
      <view class="form-card">
        <!-- 我的兴趣 -->
        <view class="form-section">
          <text class="form-label">我的兴趣</text>
          <input
            class="form-input"
            v-model="form.myInterests"
            placeholder="如：阅读、跑步、摄影（逗号分隔）"
            placeholder-style="color: #C0C0C0;"
          />
        </view>

        <!-- 我的标签 -->
        <view class="form-section">
          <text class="form-label">我的标签</text>
          <input
            class="form-input"
            v-model="form.myLabels"
            placeholder="如：程序员、二次元（逗号分隔）"
            placeholder-style="color: #C0C0C0;"
          />
        </view>

        <!-- 对方兴趣 -->
        <view class="form-section">
          <text class="form-label">对方兴趣</text>
          <input
            class="form-input"
            v-model="form.otherInterests"
            placeholder="如：音乐、旅行、美食（逗号分隔）"
            placeholder-style="color: #C0C0C0;"
          />
        </view>

        <!-- 对方标签 -->
        <view class="form-section">
          <text class="form-label">对方标签</text>
          <input
            class="form-input"
            v-model="form.otherLabels"
            placeholder="如：设计师、自由职业（逗号分隔）"
            placeholder-style="color: #C0C0C0;"
          />
        </view>

        <!-- 对方性格 -->
        <view class="form-section">
          <text class="form-label">对方性格</text>
          <input
            class="form-input"
            v-model="form.otherPersonality"
            placeholder="如：开朗健谈、有点社恐"
            placeholder-style="color: #C0C0C0;"
          />
        </view>

        <!-- 场景背景 -->
        <view class="form-section">
          <text class="form-label">场景背景</text>
          <textarea
            class="form-textarea"
            v-model="form.context"
            placeholder="如：聚会、工作会议、初次见面"
            placeholder-style="color: #C0C0C0;"
            :maxlength="200"
          />
        </view>
      </view>

      <!-- 提交按钮 -->
      <view class="submit-wrap">
        <view
          class="submit-btn"
          :class="{ 'submit-btn--disabled': !canSubmit }"
          @click="handleSubmit"
        >
          <text class="submit-btn-text">点击生成妙计</text>
        </view>
      </view>
    </scroll-view>

    <!-- ==================== Loading 态 ==================== -->
    <view v-if="viewMode === 'loading'" class="loading-state">
      <view class="loading-spinner" />
      <text class="loading-text">正在分析，请稍候…</text>
    </view>

    <!-- ==================== 结果态 ==================== -->
    <scroll-view v-if="viewMode === 'result'" class="result-scroll" scroll-y>
      <!-- 锦囊妙计横幅 -->
      <view class="result-banner">
        <text class="result-banner-text">锦囊妙计</text>
      </view>

      <!-- 开场白 / 策略 -->
      <view class="result-card">
        <text class="result-card-label">开场白</text>
        <view class="result-card-body">
          <text class="result-card-content">{{ result.strategy }}</text>
        </view>
      </view>

      <!-- 话题建议 -->
      <view class="result-card">
        <text class="result-card-label">话题建议</text>
        <view class="result-list">
          <view v-for="(topic, idx) in result.topics" :key="idx" class="result-list-item">
            <text class="result-list-num">{{ idx + 1 }}.</text>
            <text class="result-list-text">{{ topic }}</text>
          </view>
          <view v-if="!result.topics.length" class="result-empty">
            <text class="result-empty-text">暂无话题建议</text>
          </view>
        </view>
      </view>

      <!-- 避雷指南 -->
      <view class="result-card">
        <text class="result-card-label">避雷指南</text>
        <view class="result-list">
          <view v-for="(w, idx) in result.warnings" :key="idx" class="result-list-item">
            <text class="result-list-dot">●</text>
            <text class="result-list-text">{{ w }}</text>
          </view>
          <view v-if="!result.warnings.length" class="result-empty">
            <text class="result-empty-text">暂无避雷提醒</text>
          </view>
        </view>
      </view>

      <!-- 共同点 -->
      <view class="result-card">
        <text class="result-card-label">共同点</text>
        <view class="result-list">
          <view
            v-for="(p, idx) in result.commonGroundPoints"
            :key="idx"
            class="result-list-item"
          >
            <text class="result-list-dot">●</text>
            <text class="result-list-text">{{ p }}</text>
          </view>
          <view v-if="!result.commonGroundPoints.length" class="result-empty">
            <text class="result-empty-text">暂无共同点</text>
          </view>
        </view>
      </view>

      <!-- 重新分析按钮 -->
      <view class="reset-wrap">
        <view class="reset-btn" @click="handleReset">
          <text class="reset-btn-text">重新分析</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { icebreakAnalysis } from '@/api/simulation'

// ===== 视图状态 =====
type ViewMode = 'form' | 'loading' | 'result'
const viewMode = ref<ViewMode>('form')

// ===== 表单数据 =====
const form = reactive({
  myInterests: '',
  myLabels: '',
  otherInterests: '',
  otherLabels: '',
  otherPersonality: '',
  context: '',
})

// 至少有一个字段非空才能提交
const canSubmit = computed(() => {
  return Object.values(form).some((v) => v.trim().length > 0)
})

// ===== 结果数据 =====
const result = reactive({
  strategy: '',
  topics: [] as string[],
  warnings: [] as string[],
  commonGroundPoints: [] as string[],
})

// ===== 工具函数 =====
/** 以逗号/中文逗号分隔文本为数组，过滤空值 */
function splitTags(input: string): string[] {
  return input
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

/** 重置表单 */
function resetForm() {
  form.myInterests = ''
  form.myLabels = ''
  form.otherInterests = ''
  form.otherLabels = ''
  form.otherPersonality = ''
  form.context = ''
}

/** 重置结果 */
function resetResult() {
  result.strategy = ''
  result.topics = []
  result.warnings = []
  result.commonGroundPoints = []
}

// ===== 提交分析 =====
async function handleSubmit() {
  if (!canSubmit.value) return

  viewMode.value = 'loading'

  try {
    const res = await icebreakAnalysis({
      myInterests: splitTags(form.myInterests),
      myLabels: splitTags(form.myLabels),
      otherInterests: splitTags(form.otherInterests),
      otherLabels: splitTags(form.otherLabels),
      otherPersonality: form.otherPersonality.trim(),
      context: form.context.trim() || '初次见面',
    })

    if (res.code === 0) {
      const analysis = res.data.analysis
      result.strategy = analysis.strategy || ''
      result.topics = analysis.topics || []
      result.warnings = analysis.warnings || []
      result.commonGroundPoints = analysis.commonGroundPoints || []
      viewMode.value = 'result'
    } else {
      uni.showToast({ title: '分析失败，请重试', icon: 'none' })
      viewMode.value = 'form'
    }
  } catch {
    uni.showToast({ title: '网络异常，请重试', icon: 'none' })
    viewMode.value = 'form'
  }
}

// ===== 重新分析 =====
function handleReset() {
  resetResult()
  resetForm()
  viewMode.value = 'form'
}
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}

/* ==================== 顶部导航 ==================== */
.top-bar {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-bottom: 1rpx solid #eee;
  flex-shrink: 0;
}

.top-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #e63d3d;
}

/* ==================== 表单态 ==================== */
.form-scroll {
  flex: 1;
  min-height: 0;
}

.form-card {
  margin: 24rpx 30rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 32rpx 28rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.form-section {
  margin-bottom: 28rpx;
}

.form-section:last-child {
  margin-bottom: 0;
}

.form-label {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
  display: block;
  margin-bottom: 14rpx;
}

.form-input {
  width: 100%;
  height: 80rpx;
  background: #f8f8f8;
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #333;
  box-sizing: border-box;
}

.form-textarea {
  width: 100%;
  min-height: 140rpx;
  background: #f8f8f8;
  border-radius: 12rpx;
  padding: 18rpx 24rpx;
  font-size: 28rpx;
  color: #333;
  box-sizing: border-box;
}

/* 提交按钮 */
.submit-wrap {
  padding: 20rpx 30rpx 60rpx;
}

.submit-btn {
  width: 100%;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 48rpx;
  background: linear-gradient(135deg, #2dd4bf, #14b8a6);
  box-shadow: 0 6rpx 20rpx rgba(20, 184, 166, 0.35);
  transition: opacity 0.2s;
}

.submit-btn--disabled {
  opacity: 0.4;
}

.submit-btn-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
  letter-spacing: 4rpx;
}

/* ==================== Loading 态 ==================== */
.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 28rpx;
}

.loading-spinner {
  width: 64rpx;
  height: 64rpx;
  border: 6rpx solid #e8e8e8;
  border-top-color: #2dd4bf;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 28rpx;
  color: #999;
}

/* ==================== 结果态 ==================== */
.result-scroll {
  flex: 1;
  min-height: 0;
  padding-bottom: 60rpx;
}

/* 锦囊妙计横幅 */
.result-banner {
  margin: 24rpx 30rpx;
  height: 100rpx;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(59, 130, 246, 0.3);
}

.result-banner-text {
  font-size: 40rpx;
  font-weight: 700;
  color: #fff;
  letter-spacing: 8rpx;
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

.result-card-body {
  background: #f8f8f8;
  border-radius: 12rpx;
  padding: 20rpx;
  border: 1rpx solid #eee;
}

.result-card-content {
  font-size: 28rpx;
  color: #444;
  line-height: 1.7;
  white-space: pre-wrap;
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

/* 重新分析按钮 */
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

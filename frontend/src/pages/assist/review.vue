<template>
  <view class="page-container">
    <!-- 加载/评分中态 -->
    <view v-if="loading" class="loading-state">
      <view class="loading-spinner" />
      <text class="loading-text">{{ scoring ? 'AI 评分中…' : '加载中…' }}</text>
    </view>

    <scroll-view v-else-if="evaluation" class="review-scroll" scroll-y>
      <!-- 总分 + 五维条形 -->
      <view class="score-card">
        <view class="score-card__header">
          <text class="score-card__title">社交评分</text>
        </view>
        <view class="score-card__body">
          <view class="score-card__main">
            <text class="score-card__number">{{ score }}</text>
            <text class="score-card__unit">分</text>
          </view>
          <view class="score-card__dimensions">
            <view class="dimension-item">
              <text class="dimension-label">清晰度</text>
              <view class="dimension-bar">
                <view class="dimension-bar__fill dimension-bar__fill--cyan" :style="{ width: evaluation.clarity + '%' }" />
              </view>
              <text class="dimension-score">{{ evaluation.clarity }}</text>
            </view>
            <view class="dimension-item">
              <text class="dimension-label">逻辑性</text>
              <view class="dimension-bar">
                <view class="dimension-bar__fill dimension-bar__fill--purple" :style="{ width: evaluation.logicality + '%' }" />
              </view>
              <text class="dimension-score">{{ evaluation.logicality }}</text>
            </view>
            <view class="dimension-item">
              <text class="dimension-label">共情倾听</text>
              <view class="dimension-bar">
                <view class="dimension-bar__fill dimension-bar__fill--rose" :style="{ width: evaluation.empathyListening + '%' }" />
              </view>
              <text class="dimension-score">{{ evaluation.empathyListening }}</text>
            </view>
            <view class="dimension-item">
              <text class="dimension-label">互动性</text>
              <view class="dimension-bar">
                <view class="dimension-bar__fill dimension-bar__fill--amber" :style="{ width: evaluation.interactivity + '%' }" />
              </view>
              <text class="dimension-score">{{ evaluation.interactivity }}</text>
            </view>
            <view class="dimension-item">
              <text class="dimension-label">松弛感</text>
              <view class="dimension-bar">
                <view class="dimension-bar__fill dimension-bar__fill--emerald" :style="{ width: evaluation.relaxation + '%' }" />
              </view>
              <text class="dimension-score">{{ evaluation.relaxation }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- AI 评语 -->
      <view class="comment-card">
        <view class="comment-card__header">
          <text class="comment-card__title">AI 评语</text>
        </view>
        <view class="comment-card__body">
          <text class="comment-card__text">{{ evaluation.comment }}</text>
          <view class="comment-card__tags">
            <view v-if="evaluation.strengths?.length" class="tag-group">
              <text class="tag-group__label">优点</text>
              <text v-for="s in evaluation.strengths" :key="s" class="tag tag--positive">{{ s }}</text>
            </view>
            <view v-if="evaluation.suggestions?.length" class="tag-group">
              <text class="tag-group__label">建议</text>
              <text v-for="s in evaluation.suggestions" :key="s" class="tag tag--suggest">{{ s }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 我的状态自评 -->
      <view class="self-card">
        <view class="self-card__header">
          <text class="self-card__title">我的状态</text>
          <text class="self-card__hint">交流时的个人状态（可多选）</text>
        </view>
        <view class="self-card__tags">
          <view
            v-for="s in STATE_PRESETS"
            :key="s"
            class="state-tag"
            :class="{ 'state-tag--active': selectedStates.includes(s) }"
            @tap="toggleState(s)"
          >
            <text class="state-tag__text">{{ s }}</text>
          </view>
        </view>
      </view>

      <!-- 自评输入框 -->
      <view class="self-card">
        <view class="self-card__header">
          <text class="self-card__title">我的复盘</text>
          <text class="self-card__hint">写下自己的表现感受与改进点</text>
        </view>
        <textarea
          class="self-textarea"
          v-model="selfComment"
          placeholder="如：开场有点紧张，后来聊到共同话题放松多了；下次可以多提问…"
          placeholder-style="color:#c0c4cc"
          :maxlength="500"
          auto-height
        />
      </view>

      <!-- 保存自评按钮 -->
      <view class="save-wrap">
        <view class="save-btn" :class="{ 'save-btn--disabled': saving }" @tap="handleSave">
          <text class="save-btn-text">{{ saving ? '保存中…' : '保存复盘' }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- 空态 / 加载失败 -->
    <view v-else class="empty-state">
      <text class="empty-state-text">{{ loadError || '暂无复盘数据' }}</text>
      <view class="empty-state-btn" @tap="loadReview">
        <text class="empty-state-btn-text">重试</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getHistory, scoreConversation, saveSelfReview } from '@/api/simulation'
import type { EvaluationSummary } from '@/types/simulation'

// 预设状态标签（对齐评价体系 self_state 词表）
const STATE_PRESETS = ['从容', '紧张', '投入', '心累', '超常发挥', '发挥失常', '放松', '被动']

let currentSessionId = ''

const loading = ref(false)
const scoring = ref(false)
const saving = ref(false)
const loadError = ref('')
const score = ref(0)
const evaluation = ref<EvaluationSummary | null>(null)

const selectedStates = ref<string[]>([])
const selfComment = ref('')

onLoad((query: any) => {
  currentSessionId = query?.sessionId || ''
  if (!currentSessionId) {
    uni.showToast({ title: '参数错误', icon: 'none' })
    return
  }
  loadReview()
})

async function loadReview() {
  if (!currentSessionId || loading.value) return
  loading.value = true
  loadError.value = ''
  try {
    const res = await getHistory(currentSessionId)
    if (res.code === 0 && res.data) {
      // 回显已有自评
      if (res.data.selfState) {
        selectedStates.value = res.data.selfState.split(/[、,，]/).map((s) => s.trim()).filter(Boolean)
      }
      if (res.data.selfComment) {
        selfComment.value = res.data.selfComment
      }
      // 已有评分直接展示；无评分则自动触发 AI 评分
      if (res.data.evaluation) {
        score.value = res.data.evaluation.score ?? 0
        evaluation.value = res.data.evaluation
      } else if ((res.data.messages || []).length > 0) {
        await runScore()
      } else {
        loadError.value = '会话无消息，无法复盘'
      }
    } else {
      loadError.value = '加载失败'
    }
  } catch {
    loadError.value = '无法连接服务器'
  } finally {
    loading.value = false
  }
}

async function runScore() {
  scoring.value = true
  try {
    const res = await scoreConversation(currentSessionId)
    if (res.code === 0 && res.data) {
      score.value = res.data.score
      evaluation.value = res.data.evaluation
    } else {
      loadError.value = '评分失败，请重试'
    }
  } catch {
    loadError.value = '评分失败，请重试'
  } finally {
    scoring.value = false
  }
}

function toggleState(s: string) {
  const idx = selectedStates.value.indexOf(s)
  if (idx >= 0) {
    selectedStates.value.splice(idx, 1)
  } else {
    selectedStates.value.push(s)
  }
}

async function handleSave() {
  if (saving.value) return
  saving.value = true
  try {
    const res = await saveSelfReview(
      currentSessionId,
      selectedStates.value.join('、'),
      selfComment.value.trim(),
    )
    if (res.code === 0) {
      uni.showToast({ title: '复盘已保存', icon: 'success' })
    } else {
      uni.showToast({ title: '保存失败', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '网络异常，请重试', icon: 'none' })
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f0f0f0;
  display: flex;
  flex-direction: column;
}

/* 加载态 */
.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  padding-top: 300rpx;
}

.loading-spinner {
  width: 64rpx;
  height: 64rpx;
  border: 6rpx solid #e8e8e8;
  border-top-color: #5b8def;
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

/* 空态 */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 28rpx;
  padding-top: 300rpx;
}

.empty-state-text {
  font-size: 28rpx;
  color: #999;
}

.empty-state-btn {
  padding: 16rpx 64rpx;
  border-radius: 40rpx;
  background: #5b8def;
}

.empty-state-btn-text {
  font-size: 28rpx;
  color: #fff;
  font-weight: 600;
}

.review-scroll {
  flex: 1;
  min-height: 0;
  padding: 20rpx 0 60rpx;
}

/* ===== 评分卡片 ===== */
.score-card {
  margin: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.score-card__header {
  padding: 24rpx 28rpx 0;
}
.score-card__title {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
}
.score-card__body {
  display: flex;
  align-items: center;
  padding: 24rpx 28rpx 28rpx;
}
.score-card__main {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 140rpx;
  flex-shrink: 0;
  margin-right: 28rpx;
}
.score-card__number {
  font-size: 64rpx;
  font-weight: bold;
  color: #4a90d9;
  line-height: 1;
}
.score-card__unit {
  font-size: 24rpx;
  color: #999;
  margin-top: 4rpx;
}
.score-card__dimensions {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.dimension-item {
  display: flex;
  align-items: center;
}
.dimension-label {
  font-size: 24rpx;
  color: #666;
  width: 90rpx;
  flex-shrink: 0;
}
.dimension-bar {
  flex: 1;
  height: 14rpx;
  background: #f0f0f0;
  border-radius: 7rpx;
  overflow: hidden;
  margin: 0 12rpx;
}
.dimension-bar__fill {
  height: 100%;
  border-radius: 7rpx;
  background: linear-gradient(90deg, #4a90d9, #5b8def);
}
.dimension-bar__fill--cyan {
  background: linear-gradient(90deg, #06b6d4, #22d3ee);
}
.dimension-bar__fill--purple {
  background: linear-gradient(90deg, #8b5cf6, #a78bfa);
}
.dimension-bar__fill--rose {
  background: linear-gradient(90deg, #f43f5e, #fb7185);
}
.dimension-bar__fill--amber {
  background: linear-gradient(90deg, #f59e0b, #fbbf24);
}
.dimension-bar__fill--emerald {
  background: linear-gradient(90deg, #10b981, #34d399);
}
.dimension-score {
  font-size: 24rpx;
  font-weight: 600;
  color: #333;
  width: 50rpx;
  text-align: right;
  flex-shrink: 0;
}

/* ===== 评语卡片 ===== */
.comment-card {
  margin: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  overflow: hidden;
}
.comment-card__header {
  padding: 24rpx 28rpx 0;
}
.comment-card__title {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
}
.comment-card__body {
  padding: 20rpx 28rpx 28rpx;
}
.comment-card__text {
  font-size: 28rpx;
  color: #555;
  line-height: 1.7;
}
.comment-card__tags {
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.tag-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12rpx;
}
.tag-group__label {
  font-size: 24rpx;
  font-weight: 600;
  color: #666;
  margin-right: 4rpx;
}
.tag {
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
}
.tag--positive {
  color: #43b581;
  background: #e8f8f0;
}
.tag--suggest {
  color: #f5a623;
  background: #fff6e5;
}

/* ===== 自评卡片 ===== */
.self-card {
  margin: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  padding: 24rpx 28rpx 28rpx;
}
.self-card__header {
  display: flex;
  align-items: baseline;
  gap: 16rpx;
  margin-bottom: 20rpx;
}
.self-card__title {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
}
.self-card__hint {
  font-size: 22rpx;
  color: #999;
}
.self-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.state-tag {
  padding: 12rpx 28rpx;
  border-radius: 32rpx;
  background: #f3f4f6;
  border: 2rpx solid transparent;
  transition: all 0.15s ease;
}
.state-tag--active {
  background: #ebf0ff;
  border-color: #5b8def;
}
.state-tag__text {
  font-size: 26rpx;
  color: #555;
}
.state-tag--active .state-tag__text {
  color: #5b8def;
  font-weight: 500;
}
.self-textarea {
  width: 100%;
  min-height: 160rpx;
  background: #f8f8f8;
  border-radius: 12rpx;
  padding: 18rpx 24rpx;
  font-size: 28rpx;
  color: #333;
  box-sizing: border-box;
  line-height: 1.6;
}

/* ===== 保存按钮 ===== */
.save-wrap {
  padding: 24rpx 30rpx 40rpx;
}
.save-btn {
  width: 100%;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 48rpx;
  background: linear-gradient(135deg, #5b8def, #3b6fd4);
  box-shadow: 0 6rpx 20rpx rgba(59, 111, 212, 0.35);
}
.save-btn--disabled {
  opacity: 0.6;
}
.save-btn-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
  letter-spacing: 4rpx;
}
</style>

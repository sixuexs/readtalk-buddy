<template>
  <view class="page-container">
      <!-- 未评分提示 -->
      <view v-if="!evaluation && !scoring" class="score-pending">
        <text class="score-pending__text">暂未评分</text>
        <view class="score-pending__btn" @click="handleScore">
          <text>AI 智能评分</text>
        </view>
      </view>
      <view v-if="scoring" class="score-pending">
        <text class="score-pending__text">AI 评分中...</text>
      </view>

      <!-- 评分卡片 -->
      <view v-if="evaluation" class="score-card">
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

      <!-- 评语卡片 -->
      <view v-if="evaluation" class="comment-card">
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

      <!-- 聊天分割线 -->
      <view class="chat-divider">
        <view class="chat-divider__line" />
        <text class="chat-divider__text">聊天记录</text>
        <view class="chat-divider__line" />
      </view>

      <!-- 消息时间线 -->
      <view v-if="messages.length === 0" class="empty-msg">
        <text>暂无消息记录</text>
      </view>
      <view v-else class="timeline">
        <view
          v-for="msg in messages"
          :key="msg.id"
          :id="'msg-' + msg.id"
          class="timeline-item"
        >
          <!-- 时间线节点 -->
          <view class="timeline-node">
            <view
              class="timeline-dot"
              :class="msg.role === 'self' ? 'timeline-dot--self' : 'timeline-dot--other'"
            />
            <view class="timeline-line" />
          </view>
          <!-- 消息卡片 -->
          <view
            class="msg-card"
            :class="msg.role === 'self' ? 'msg-card--self' : 'msg-card--other'"
          >
            <view class="msg-card__header">
              <text
                class="msg-card__role"
                :class="msg.role === 'self' ? 'msg-card__role--self' : 'msg-card__role--other'"
              >{{ msg.role === 'self' ? '你' : '对方' }}</text>
              <text class="msg-card__time">{{ formatMsgTime(msg.timestamp) }}</text>
            </view>
            <text class="msg-card__content">{{ msg.content }}</text>
          </view>
        </view>
        <view id="msg-anchor" />
      </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getHistory, scoreConversation } from '@/api/simulation'
import type { Message, EvaluationSummary } from '@/types/simulation'

const messages = ref<Message[]>([])
const score = ref<number>(0)
const evaluation = ref<EvaluationSummary | null>(null)
const scoring = ref(false)
let currentSessionId = ''

// 保留 mock 数据作为开发时的 fallback（后端无评分时使用）
const mockMessagesMap: Record<string, { score: number; messages: Message[] }> = {
  'demo-1': {
    score: 85,
    messages: [
      { id: 'd1-1', role: 'other', avatar: '', content: '你好呀！很高兴认识你，你是第一次参加这种活动吗？', timestamp: Date.now() - 600000 },
      { id: 'd1-2', role: 'self', avatar: '', content: '你好！是的，第一次来，有点紧张哈哈。', timestamp: Date.now() - 540000 },
      { id: 'd1-3', role: 'other', avatar: '', content: '别紧张，大家都很友好的。你平时喜欢做什么呀？', timestamp: Date.now() - 480000 },
      { id: 'd1-4', role: 'self', avatar: '', content: '我比较喜欢看书和跑步，最近在看一本很有意思的小说。', timestamp: Date.now() - 420000 },
      { id: 'd1-5', role: 'other', avatar: '', content: '听起来很棒！是什么类型的小说？我也想找点新书看。', timestamp: Date.now() - 360000 },
      { id: 'd1-6', role: 'self', avatar: '', content: '是悬疑推理类的，剧情很紧凑，推荐给你！', timestamp: Date.now() - 300000 },
    ],
  },
  'demo-2': {
    score: 72,
    messages: [
      { id: 'd2-1', role: 'other', avatar: '', content: '小李，关于这个项目的进度，我想和你确认一下。', timestamp: Date.now() - 900000 },
      { id: 'd2-2', role: 'self', avatar: '', content: '好的，您说。目前前端部分已经完成了 80%。', timestamp: Date.now() - 840000 },
      { id: 'd2-3', role: 'other', avatar: '', content: '那后端接口部分呢？有没有遇到什么阻塞？', timestamp: Date.now() - 780000 },
      { id: 'd2-4', role: 'self', avatar: '', content: '接口部分还在联调，预计明天可以全部完成。', timestamp: Date.now() - 720000 },
    ],
  },
  'demo-3': {
    score: 91,
    messages: [
      { id: 'd3-1', role: 'other', avatar: '', content: '我觉得这个方案有些问题，想和你讨论一下。', timestamp: Date.now() - 1200000 },
      { id: 'd3-2', role: 'self', avatar: '', content: '好的，我理解你的顾虑，可以先说说具体是哪方面的问题吗？', timestamp: Date.now() - 1140000 },
      { id: 'd3-3', role: 'other', avatar: '', content: '主要是成本方面，我觉得预算有点超了。', timestamp: Date.now() - 1080000 },
      { id: 'd3-4', role: 'self', avatar: '', content: '确实，我也注意到了。不过我们可以分阶段执行，先做核心功能，控制初期成本。', timestamp: Date.now() - 1020000 },
      { id: 'd3-5', role: 'other', avatar: '', content: '这个思路不错，能具体说说第一阶段包含哪些功能吗？', timestamp: Date.now() - 960000 },
      { id: 'd3-6', role: 'self', avatar: '', content: '第一阶段先做用户注册登录、核心业务流和基础报表，其他功能后续迭代。', timestamp: Date.now() - 900000 },
      { id: 'd3-7', role: 'other', avatar: '', content: '好的，这样的话我觉得可以接受。谢谢你的耐心解释！', timestamp: Date.now() - 840000 },
      { id: 'd3-8', role: 'self', avatar: '', content: '没问题，有问题随时沟通！', timestamp: Date.now() - 780000 },
    ],
  },
}

onLoad(async (query: any) => {
  const sessionId = query?.sessionId
  currentSessionId = sessionId
  if (!sessionId) {
    uni.showToast({ title: '参数错误', icon: 'none' })
    return
  }

  // 判断是否为 mock 数据（开发时使用）
  const mockData = mockMessagesMap[sessionId]
  if (mockData) {
    score.value = mockData.score
    messages.value = mockData.messages
    return
  }

  try {
    const res = await getHistory(sessionId)
    if (res.code === 0 && res.data?.messages) {
      messages.value = res.data.messages
      // 使用后端真实评分数据
      if (res.data.evaluation) {
        evaluation.value = res.data.evaluation
      }
      if (res.data.evaluation?.score != null) {
        score.value = res.data.evaluation.score
      }
    }
  } catch {
    uni.showToast({ title: '加载失败', icon: 'none' })
  }
})

const handleScore = async () => {
  if (!currentSessionId || scoring.value) return
  scoring.value = true
  try {
    const res = await scoreConversation(currentSessionId)
    if (res.code === 0 && res.data) {
      score.value = res.data.score
      evaluation.value = res.data.evaluation
      uni.showToast({ title: '评分完成', icon: 'success' })
    }
  } catch {
    uni.showToast({ title: '评分失败', icon: 'none' })
  } finally {
    scoring.value = false
  }
}

const formatMsgTime = (ts: number): string => {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
/* 评分按钮 */
.score-pending {
  margin: 24rpx;
  padding: 40rpx;
  background: #fff;
  border-radius: 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}
.score-pending__text {
  font-size: 28rpx;
  color: #999;
}
.score-pending__btn {
  background: linear-gradient(135deg, #4A90D9, #5B8DEF);
  padding: 16rpx 48rpx;
  border-radius: 40rpx;
}
.score-pending__btn text {
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}

.page-container {
  min-height: 100vh;
  background: #f0f0f0;
  padding: 20rpx 0 40rpx;
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
  color: #4A90D9;
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
  background: linear-gradient(90deg, #4A90D9, #5B8DEF);
}
.dimension-bar__fill--cyan {
  background: linear-gradient(90deg, #06B6D4, #22D3EE);
}
.dimension-bar__fill--purple {
  background: linear-gradient(90deg, #8B5CF6, #A78BFA);
}
.dimension-bar__fill--rose {
  background: linear-gradient(90deg, #F43F5E, #FB7185);
}
.dimension-bar__fill--amber {
  background: linear-gradient(90deg, #F59E0B, #FBBF24);
}
.dimension-bar__fill--emerald {
  background: linear-gradient(90deg, #10B981, #34D399);
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
  color: #43B581;
  background: #E8F8F0;
}
.tag--suggest {
  color: #F5A623;
  background: #FFF6E5;
}

/* ===== 聊天分割线 ===== */
.chat-divider {
  display: flex;
  align-items: center;
  margin: 24rpx 48rpx 16rpx;
}
.chat-divider__line {
  flex: 1;
  height: 1px;
  background: #ddd;
}
.chat-divider__text {
  font-size: 24rpx;
  color: #999;
  margin: 0 20rpx;
}

/* ===== 消息时间线 ===== */
.empty-msg {
  display: flex;
  justify-content: center;
  padding-top: 200rpx;
  color: #999;
  font-size: 28rpx;
}

.timeline {
  padding: 0 24rpx 20rpx 24rpx;
}

.timeline-item {
  display: flex;
  align-items: stretch;
}

/* 时间线左侧节点 */
.timeline-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 32rpx;
  flex-shrink: 0;
  margin-right: 16rpx;
}
.timeline-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 24rpx;
}
.timeline-dot--self {
  background: #4A90D9;
}
.timeline-dot--other {
  background: #F5A623;
}
.timeline-line {
  width: 2rpx;
  flex: 1;
  background: #e0e0e0;
  margin-top: 8rpx;
}
.timeline-item:last-child .timeline-line {
  display: none;
}

/* 消息卡片 */
.msg-card {
  flex: 1;
  background: #fff;
  border-radius: 12rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.05);
  overflow: hidden;
  border-left: 6rpx solid transparent;
}
.msg-card--self {
  border-left-color: #4A90D9;
  background: #F7FAFF;
}
.msg-card--other {
  border-left-color: #F5A623;
  background: #FFFAF3;
}
.msg-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 20rpx 0;
}
.msg-card__role {
  font-size: 24rpx;
  font-weight: 600;
  padding: 2rpx 14rpx;
  border-radius: 6rpx;
}
.msg-card__role--self {
  color: #4A90D9;
  background: #EBF3FC;
}
.msg-card__role--other {
  color: #D4850A;
  background: #FFF0D6;
}
.msg-card__time {
  font-size: 22rpx;
  color: #bbb;
}
.msg-card__content {
  display: block;
  padding: 12rpx 20rpx 20rpx;
  font-size: 28rpx;
  color: #444;
  line-height: 1.6;
  word-break: break-all;
}
</style>

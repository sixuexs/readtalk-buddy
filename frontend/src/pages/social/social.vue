<template>
  <view class="page-container">
    <!-- 空状态 -->
    <view v-if="sessions.length === 0 && !loading" class="empty-state">
      <text class="empty-icon">📋</text>
      <text class="empty-text">暂无社交记录</text>
      <text class="empty-hint">去「情景模拟」开始一段对话吧</text>
    </view>

    <!-- 列表 -->
    <scroll-view v-else class="list-scroll" scroll-y>
      <view
        v-for="item in sessions"
        :key="item.sessionId"
        class="record-item"
        @click="goChatHistory(item.sessionId, item.score)"
      >
        <!-- 左侧：评分 -->
        <view class="score-area">
          <view class="score-circle">
            <text class="score-num">{{ item.score ?? '--' }}</text>
            <text class="score-label">分</text>
          </view>
        </view>

        <!-- 右侧：信息 -->
        <view class="info-area">
          <view class="info-top">
            <text class="info-theme">{{ item.theme }}</text>
            <text class="info-personality">{{ item.personality }}</text>
          </view>
          <view class="info-bottom">
            <text class="info-time">{{ formatTime(item.lastActivity) }}</text>
            <text class="info-count">{{ item.messageCount }} 条消息</text>
          </view>
        </view>

        <!-- 箭头 -->
        <text class="arrow">›</text>
      </view>
    </scroll-view>

    <CustomTabBar />
    <FloatingActionButton />
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import FloatingActionButton from '@/components/FloatingActionButton.vue'
import { getSessions } from '@/api/simulation'
import type { SessionSummary } from '@/types/simulation'

const sessions = ref<SessionSummary[]>([])
const loading = ref(true)

// ===== 前端示例数据（后端未返回时使用） =====
const mockSessions: SessionSummary[] = [
  {
    sessionId: 'demo-1',
    theme: '初次见面',
    personality: '开朗健谈',
    score: 85,
    messageCount: 6,
    lastActivity: Date.now() - 3600000,
    evaluation: null,
  },
  {
    sessionId: 'demo-2',
    theme: '职场沟通',
    personality: '沉稳内敛',
    score: 72,
    messageCount: 4,
    lastActivity: Date.now() - 86400000,
    evaluation: null,
  },
  {
    sessionId: 'demo-3',
    theme: '冲突化解',
    personality: '直率坦诚',
    score: 91,
    messageCount: 8,
    lastActivity: Date.now() - 172800000,
    evaluation: null,
  },
]

onMounted(async () => {
  try {
    const res = await getSessions()
    if (res.code === 0 && res.data && res.data.length > 0) {
      sessions.value = res.data.map((s) => ({
        ...s,
        score: s.score ?? 75,
      }))
    } else {
      // 后端无数据，使用示例数据
      sessions.value = mockSessions
    }
  } catch {
    // 加载失败，使用示例数据
    sessions.value = mockSessions
  } finally {
    loading.value = false
  }
})

// 跳转到聊天记录（只读）
const goChatHistory = (sessionId: string, score?: number | null) => {
  const scoreParam = score != null ? `&score=${score}` : ''
  uni.navigateTo({ url: `/pages/social/chat-history?sessionId=${sessionId}${scoreParam}` })
}

// 格式化时间
const formatTime = (ts: number): string => {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f5f5f5;
  padding-bottom: 140rpx;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 60vh;
}
.empty-icon {
  font-size: 80rpx;
  margin-bottom: 20rpx;
}
.empty-text {
  font-size: 32rpx;
  color: #999;
}
.empty-hint {
  font-size: 26rpx;
  color: #ccc;
  margin-top: 10rpx;
}

/* 列表 */
.list-scroll {
  height: 100vh;
}

.record-item {
  display: flex;
  align-items: center;
  background: #fff;
  margin: 16rpx 24rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

/* 左侧评分 */
.score-area {
  margin-right: 24rpx;
  flex-shrink: 0;
}
.score-circle {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #4A90D9, #5B8DEF);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.score-num {
  font-size: 36rpx;
  font-weight: bold;
  color: #fff;
  line-height: 1.1;
}
.score-label {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.8);
}

/* 右侧信息 */
.info-area {
  flex: 1;
  overflow: hidden;
}
.info-top {
  display: flex;
  align-items: center;
  margin-bottom: 8rpx;
}
.info-theme {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
  margin-right: 16rpx;
}
.info-personality {
  font-size: 24rpx;
  color: #4A90D9;
  background: #EBF3FC;
  padding: 2rpx 14rpx;
  border-radius: 6rpx;
}
.info-bottom {
  display: flex;
  align-items: center;
}
.info-time {
  font-size: 24rpx;
  color: #999;
  margin-right: 20rpx;
}
.info-count {
  font-size: 24rpx;
  color: #ccc;
}

/* 箭头 */
.arrow {
  font-size: 40rpx;
  color: #ccc;
  margin-left: 12rpx;
  flex-shrink: 0;
}
</style>

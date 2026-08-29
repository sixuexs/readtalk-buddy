<template>
  <view class="page-container">
    <!-- 空状态 -->
    <view v-if="sessions.length === 0 && !loading" class="empty-state">
      <text class="empty-icon">📋</text>
      <text class="empty-text">暂无社交记录</text>
      <text class="empty-hint">去「情景模拟」开始一段对话吧</text>
    </view>

    <!-- 列表：条目支持左滑露出删除按钮 -->
    <scroll-view v-else class="list-scroll" scroll-y>
      <view
        v-for="item in sessions"
        :key="item.sessionId"
        class="swipe-cell"
      >
        <!-- 底层删除按钮（左滑露出） -->
        <view class="swipe-delete" @click.stop="handleDelete(item)">
          <text class="swipe-delete-text">删除</text>
        </view>

        <!-- 内容层：跟手平移 -->
        <view
          class="swipe-content"
          :class="{ 'swipe-content--dragging': draggingId === item.sessionId }"
          :style="{ transform: `translateX(${offsetOf(item)}px)` }"
          @touchstart="onTouchStart(item, $event)"
          @touchmove="onTouchMove(item, $event)"
          @touchend="onTouchEnd(item)"
          @touchcancel="onTouchEnd(item)"
          @click="onItemClick(item)"
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
import { getSessions, deleteSession } from '@/api/simulation'
import type { SessionSummary } from '@/types/simulation'

const sessions = ref<SessionSummary[]>([])
const loading = ref(true)

// ===== 前端示例数据（后端未返回时使用，15 条占位） =====
const MOCK_THEMES = [
  '初次见面', '读书交流', '读后感分享', '面试演练', '日常闲聊',
  '职场沟通', '冲突化解', '小组讨论', '社团活动', '导师沟通',
  '读书会破冰', '观点交锋', '新书推荐', '小组汇报', '茶话闲谈',
]
const MOCK_PERSONALITIES = ['乐观开朗自来熟', '不善交际慢热', '幽默风趣社牛', '沉稳内敛观察者']
const MOCK_SCORES = [88, 76, 91, 64, 82, 70, 95, 58, 79, 85, 68, 90, 73, 61, 84]
const MOCK_COUNTS = [6, 8, 12, 4, 10, 7, 16, 5, 9, 11, 6, 14, 8, 5, 13]
// 时间从 1 小时前开始，逐条递增（约三周跨度）
const MOCK_AGE_HOURS = [1, 10, 26, 34, 50, 72, 96, 120, 150, 180, 220, 260, 320, 420, 520]

const mockSessions: SessionSummary[] = MOCK_THEMES.map((theme, i) => ({
  sessionId: `demo-${i + 1}`,
  theme,
  personality: MOCK_PERSONALITIES[i % MOCK_PERSONALITIES.length],
  score: MOCK_SCORES[i],
  messageCount: MOCK_COUNTS[i],
  lastActivity: Date.now() - MOCK_AGE_HOURS[i] * 3600000,
  evaluation: null,
}))

// ===== 左滑删除手势 =====
/** 删除按钮宽度（px），与样式 .swipe-delete 的 144rpx 对应 */
const DELETE_W = 70

// 当前左滑展开的条目（同一时刻最多一条）
const openId = ref('')
// 正在拖动的条目与实时偏移（拖动中关闭过渡动画，跟手）
const draggingId = ref('')
const dragOffset = ref(0)

let startX = 0
let startY = 0
let startOffset = 0
// null=未判定；true=水平滑动（接管）；false=垂直滑动（交给列表滚动）
let directionLocked: boolean | null = null
// 水平滑动后抑制本次点击，避免左滑误触进入详情
let clickSuppressed = false

function offsetOf(item: SessionSummary): number {
  if (draggingId.value === item.sessionId) return dragOffset.value
  if (openId.value === item.sessionId) return -DELETE_W
  return 0
}

function onTouchStart(item: SessionSummary, e: TouchEvent) {
  const touch = e.touches?.[0]
  if (!touch) return
  startX = touch.clientX
  startY = touch.clientY
  startOffset = offsetOf(item)
  dragOffset.value = startOffset
  draggingId.value = item.sessionId
  directionLocked = null
  clickSuppressed = false
  // 滑动新条目时收起已展开的其他条目
  if (openId.value && openId.value !== item.sessionId) {
    openId.value = ''
  }
}

function onTouchMove(item: SessionSummary, e: TouchEvent) {
  if (draggingId.value !== item.sessionId) return
  const touch = e.touches?.[0]
  if (!touch) return
  const dx = touch.clientX - startX
  const dy = touch.clientY - startY

  // 位移超过阈值后锁定方向：水平则接管，垂直则放行给列表滚动
  if (directionLocked === null) {
    if (Math.abs(dx) < 8 && Math.abs(dy) < 8) return
    directionLocked = Math.abs(dx) > Math.abs(dy)
    if (directionLocked) clickSuppressed = true
  }
  if (!directionLocked) return

  // 只允许向左滑出删除按钮，右滑最多回到 0
  dragOffset.value = Math.min(0, Math.max(-DELETE_W, startOffset + dx))
}

function onTouchEnd(item: SessionSummary) {
  if (draggingId.value !== item.sessionId) return
  // 越过一半吸附为展开，否则收起
  if (directionLocked) {
    openId.value = dragOffset.value < -DELETE_W / 2 ? item.sessionId : ''
  }
  draggingId.value = ''
  directionLocked = null
}

function onItemClick(item: SessionSummary) {
  if (clickSuppressed) {
    clickSuppressed = false
    return
  }
  // 展开状态下点击内容先收起，不跳转
  if (openId.value === item.sessionId) {
    openId.value = ''
    return
  }
  goChatHistory(item.sessionId, item.score)
}

// 删除记录：demo 占位仅本地移除，真实会话调后端删除（联动重算关联书友亲密度）
function handleDelete(item: SessionSummary) {
  uni.showModal({
    title: '删除记录',
    content: `确定删除「${item.theme}」这条社交记录吗？`,
    success: async (res) => {
      if (!res.confirm) return
      // demo 占位数据后端不存在，直接本地移除
      if (item.sessionId.startsWith('demo-')) {
        removeLocal(item.sessionId)
        return
      }
      try {
        const result = await deleteSession(item.sessionId)
        if (result.code === 0) {
          removeLocal(item.sessionId)
          uni.showToast({ title: '已删除', icon: 'none' })
        } else {
          uni.showToast({ title: '删除失败，请重试', icon: 'none' })
        }
      } catch {
        uni.showToast({ title: '网络异常，删除失败', icon: 'none' })
      }
    },
  })
}

function removeLocal(sessionId: string) {
  sessions.value = sessions.value.filter((s) => s.sessionId !== sessionId)
  openId.value = ''
  uni.showToast({ title: '已删除', icon: 'none' })
}

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

/* 滑动单元格：外层裁剪，删除按钮垫在底层 */
.swipe-cell {
  position: relative;
  margin: 16rpx 24rpx;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.swipe-delete {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 140rpx;
  background: #ef4444;
  display: flex;
  align-items: center;
  justify-content: center;
}

.swipe-delete-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #fff;
}

/* 内容层：默认带回弹过渡，拖动中关闭以跟手 */
.swipe-content {
  position: relative;
  z-index: 1;
  background: #fff;
  display: flex;
  align-items: center;
  padding: 24rpx;
  transition: transform 0.2s ease;
}

.swipe-content--dragging {
  transition: none;
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

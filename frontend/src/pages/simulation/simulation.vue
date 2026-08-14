<template>
  <!-- ==================== 配置入口页 ==================== -->
  <view v-if="pageState === 'config'" class="config-page">
    <!-- 顶部品牌区 -->
    <view class="brand-area">
      <text class="brand-icon">🎬</text>
      <text class="brand-title">情景模拟</text>
      <text class="brand-subtitle">用剧本演绎你的社交故事</text>
    </view>

    <!-- 配置卡片 -->
    <view class="config-card">
      <!-- 主题选择 -->
      <view class="config-section">
        <text class="config-label">📝 选择主题</text>
        <view class="tag-group">
          <view
            v-for="t in themeOptions"
            :key="t"
            class="tag"
            :class="{ 'tag--active': selectedTheme === t }"
            @click="selectedTheme = t"
          >
            <text class="tag-text">{{ t }}</text>
          </view>
        </view>
      </view>

      <!-- 分割线 -->
      <view class="divider" />

      <!-- 性格选择 -->
      <view class="config-section">
        <text class="config-label">🎭 选择性格</text>
        <view class="tag-group">
          <view
            v-for="p in personalityOptions"
            :key="p"
            class="tag"
            :class="{ 'tag--active': selectedPersonality === p }"
            @click="selectedPersonality = p"
          >
            <text class="tag-text">{{ p }}</text>
          </view>
        </view>
      </view>

      <!-- 分割线 -->
      <view class="divider" />

      <!-- 选择练习对象（书友，可选）：选中后本场模拟评分计入该书友亲密度 -->
      <view class="config-section">
        <text class="config-label">👤 选择练习对象（可选）</text>
        <view v-if="!contactOptions.length" class="contact-empty">
          <text class="contact-empty-text">暂无书友，可跳过（纯能力训练）</text>
        </view>
        <view v-else class="tag-group">
          <view
            class="tag"
            :class="{ 'tag--active': selectedContactId === '' }"
            @click="selectedContactId = ''"
          >
            <text class="tag-text">不指定</text>
          </view>
          <view
            v-for="c in contactOptions"
            :key="c.id"
            class="tag"
            :class="{ 'tag--active': selectedContactId === c.id }"
            @click="selectedContactId = c.id"
          >
            <text class="tag-text">{{ c.name }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 随机按钮 -->
    <view class="random-btn" @click="randomConfig">
      <text class="random-btn-text">🎲 随机配置</text>
    </view>

    <!-- 底部开始按钮 -->
    <view class="start-btn-wrap">
      <view
        class="start-btn"
        :class="{ 'start-btn--disabled': !canStart }"
        @click="handleStart"
      >
        <text class="start-btn-text">开始模拟</text>
      </view>
    </view>

    <CustomTabBar />
    <FloatingActionButton />
  </view>

  <!-- ==================== 聊天页 ==================== -->
  <!-- 聊天页不显示底部 tab bar，避免键盘弹起时漏出 -->
  <view v-else class="chat-page">
    <!-- 顶部标题栏：返回 + 所选主题 -->
    <view class="title-bar">
      <view class="title-back" @click="pageState = 'config'">
        <text class="title-back-icon">←</text>
      </view>
      <text class="title-text">{{ selectedTheme }}</text>
      <view class="title-placeholder" />
    </view>

    <!-- 聊天区域 -->
    <scroll-view
      class="chat-area"
      scroll-y
      :scroll-into-view="scrollIntoId"
      scroll-with-animation
      :style="{ paddingBottom: chatPaddingBottom + 'px' }"
    >
      <view class="msg-list">
        <view
          v-for="msg in messages"
          :key="msg.id"
          :id="'msg-' + msg.id"
          class="msg-row"
          :class="msg.role === 'self' ? 'msg-row--right' : 'msg-row--left'"
        >
          <template v-if="msg.role === 'other'">
            <image class="avatar" :src="msg.avatar" mode="aspectFill" />
            <view class="bubble bubble--other">
              <text class="bubble-text">{{ msg.content }}</text>
            </view>
          </template>
          <template v-else>
            <view class="bubble bubble--self">
              <text class="bubble-text">{{ msg.content }}</text>
            </view>
            <image class="avatar" :src="msg.avatar" mode="aspectFill" />
          </template>
        </view>
        <view id="msg-anchor" />
      </view>
    </scroll-view>

    <!-- 底部输入区（fixed + 键盘高度动态调整） -->
    <view class="input-bar" :style="{ bottom: inputBarBottom + 'px' }">
      <input
        ref="inputRef"
        class="input-box"
        v-model="inputText"
        placeholder="输入你的回复..."
        placeholder-style="color: #C0C0C0;"
        confirm-type="send"
        maxlength="500"
        :adjust-position="false"
        :hold-keyboard="true"
        @confirm="handleSend"
      />
      <button class="send-btn" :disabled="!inputText.trim()" @click="handleSend">
        发送
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted, onUnmounted } from 'vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import FloatingActionButton from '@/components/FloatingActionButton.vue'
import { getConfig, startSimulation, sendMessage } from '@/api/simulation'
import { getRelationGraph } from '@/api/relation'
import type { Message } from '@/types/simulation'
import type { GraphContact } from '@/types/relationGraph'

// ===== 页面状态 =====
const pageState = ref<'config' | 'chat'>('config')

// ===== 配置页数据 =====
// 死数据，后续从 getConfig() API 获取
const themeOptions = ref<string[]>(['初次见面', '读书交流', '读后感分享', '面试演练', '日常闲聊'])
const personalityOptions = ref<string[]>(['乐观开朗自来熟', '不善交际慢热', '幽默风趣社牛', '沉稳内敛观察者'])
const selectedTheme = ref('')
const selectedPersonality = ref('')
const canStart = computed(() => !!selectedTheme.value && !!selectedPersonality.value)

// 练习对象（书友，可选）：从关系图谱拉取，选中后该场模拟评分会计入其亲密度
const contactOptions = ref<GraphContact[]>([])
const selectedContactId = ref('')

// 随机填充配置
function randomConfig() {
  const ti = Math.floor(Math.random() * themeOptions.value.length)
  const pi = Math.floor(Math.random() * personalityOptions.value.length)
  selectedTheme.value = themeOptions.value[ti]
  selectedPersonality.value = personalityOptions.value[pi]
}

// 拉取书友列表（供"选择练习对象"使用）
async function loadContactOptions() {
  try {
    const res = await getRelationGraph()
    if (res.code === 0 && res.data) {
      contactOptions.value = res.data.contacts || []
    }
  } catch {
    // 拉取失败则书友列表为空，不影响纯能力训练
  }
}

// TODO: onMounted 时调用 getConfig() 拉取远程配置，替换死数据
// onMounted(async () => {
//   const res = await getConfig()
//   if (res.code === 0) {
//     themeOptions.value = res.data.themes
//     personalityOptions.value = res.data.personalities
//   }
// })

// ===== 聊天页数据 =====
const otherAvatar = '/static/头像示例/开心鸭.jpeg'
const selfAvatar = '/static/头像示例/v2-9c5597621d3d2d2070f30633ada5822a_r.jpg'

const messages = reactive<Message[]>([])
const sessionId = ref('')
const inputText = ref('')
const scrollIntoId = ref('msg-anchor')
const inputRef = ref<{ focus: () => void } | null>(null)
let msgIdCounter = 0

// 键盘高度（px），0 表示键盘收起
const keyboardHeight = ref(0)
// 输入栏近似高度 px（约 104rpx 换算）
const inputBarHeight = 60
const inputBarBottom = computed(() => keyboardHeight.value)
const chatPaddingBottom = computed(() => inputBarHeight + keyboardHeight.value)

onMounted(() => {
  uni.onKeyboardHeightChange((res: { height: number }) => {
    keyboardHeight.value = res.height
  })
  loadContactOptions()
})

// 开始模拟：调 startSimulation 获取开场白 + sessionId
async function handleStart() {
  if (!canStart.value) return

  try {
    const res = await startSimulation({
      theme: selectedTheme.value,
      personality: selectedPersonality.value,
      relatedContactId: selectedContactId.value || undefined,
    })
    if (res.code === 0) {
      sessionId.value = res.data.sessionId
      msgIdCounter = 1
      messages.length = 0
      messages.push({
        id: String(msgIdCounter),
        role: 'other',
        avatar: otherAvatar,
        content: res.data.greeting.content,
        timestamp: res.data.greeting.timestamp,
      })
    }
  } catch {
    // 后端不可用时本地降级
    sessionId.value = 'local-' + Date.now()
    msgIdCounter = 1
    messages.length = 0
    messages.push({
      id: String(msgIdCounter),
      role: 'other',
      avatar: otherAvatar,
      content: '你好！很高兴认识你。',
      timestamp: Date.now(),
    })
  }

  pageState.value = 'chat'
  scrollIntoId.value = 'msg-anchor'
}

// 发送消息
async function handleSend() {
  const text = inputText.value.trim()
  if (!text) return

  msgIdCounter++
  messages.push({
    id: String(msgIdCounter),
    role: 'self',
    avatar: selfAvatar,
    content: text,
    timestamp: Date.now(),
  })
  inputText.value = ''
  await scrollToBottom()

  // 保持键盘不收起
  nextTick(() => {
    inputRef.value?.focus()
  })

  try {
    const res = await sendMessage({ scenarioId: sessionId.value, message: text })
    if (res.code === 0) {
      msgIdCounter++
      messages.push({
        id: String(msgIdCounter),
        role: 'other',
        avatar: otherAvatar,
        content: res.data.reply.content,
        timestamp: res.data.reply.timestamp,
      })
      await scrollToBottom()
    }
  } catch {
    uni.showToast({ title: '网络异常，请重试', icon: 'none' })
  }
}

async function scrollToBottom() {
  scrollIntoId.value = ''
  await nextTick()
  scrollIntoId.value = 'msg-anchor'
}
</script>

<style scoped>
/* ================================================================ */
/*  配置页样式                                                       */
/* ================================================================ */

.config-page {
  min-height: 90vh;
  background-color: #F2F3F5;
  display: flex;
  flex-direction: column;
  padding-bottom: 140rpx;
}

/* 品牌区 */
.brand-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 0 40rpx;
  background: linear-gradient(180deg, #1A1A2E 0%, #16213E 60%, #F2F3F5 100%);
}

.brand-icon {
  font-size: 72rpx;
  margin-bottom: 20rpx;
}

.brand-title {
  font-size: 44rpx;
  font-weight: 700;
  color: #FFFFFF;
  letter-spacing: 6rpx;
}

.brand-subtitle {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.6);
  margin-top: 12rpx;
  letter-spacing: 2rpx;
}

/* 配置卡片 */
.config-card {
  margin: -20rpx 30rpx 0;
  background-color: #FFFFFF;
  border-radius: 24rpx;
  padding: 36rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.08);
}

.config-section {
  margin-bottom: 8rpx;
}

.config-label {
  font-size: 28rpx;
  font-weight: 600;
  color: #333333;
  display: block;
  margin-bottom: 24rpx;
}

.tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
}

.tag {
  padding: 16rpx 32rpx;
  border-radius: 32rpx;
  background-color: #F5F5F5;
  border: 2rpx solid transparent;
  transition: all 0.2s ease;
}

.tag--active {
  background-color: #EBF0FF;
  border-color: #5B8DEF;
}

.tag-text {
  font-size: 26rpx;
  color: #555555;
}

.tag--active .tag-text {
  color: #5B8DEF;
  font-weight: 500;
}

.divider {
  height: 1rpx;
  background-color: #F0F0F0;
  margin: 24rpx 0;
}

.contact-empty {
  padding: 16rpx 24rpx;
  background-color: #F9FAFB;
  border-radius: 16rpx;
}

.contact-empty-text {
  font-size: 24rpx;
  color: #9CA3AF;
}

/* 随机按钮 */
.random-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 32rpx 30rpx 0;
  padding: 20rpx 0;
  border-radius: 16rpx;
  background-color: #FFFFFF;
  border: 2rpx dashed #D0D0D0;
}

.random-btn-text {
  font-size: 26rpx;
  color: #888888;
}

/* 开始按钮 */
.start-btn-wrap {
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 60rpx 30rpx 40rpx;
}

.start-btn {
  width: 100%;
  height: 100rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50rpx;
  background: linear-gradient(135deg, #5B8DEF, #3B6FD4);
  box-shadow: 0 8rpx 24rpx rgba(59, 111, 212, 0.4);
  transition: opacity 0.2s ease;
}

.start-btn--disabled {
  opacity: 0.35;
}

.start-btn-text {
  font-size: 34rpx;
  font-weight: 600;
  color: #FFFFFF;
  letter-spacing: 4rpx;
}

/* ================================================================ */
/*  聊天页样式                                                       */
/* ================================================================ */

.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #F2F3F5;
}
.title-bar {
  display: flex;
  align-items: center;
  height: 90rpx;
  background-color: #FFFFFF;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.05);
  padding: 0 24rpx;
}

.title-back {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.title-back-icon {
  font-size: 36rpx;
  color: #5B8DEF;
  font-weight: 600;
}

.title-text {
  flex: 1;
  text-align: center;
  font-size: 34rpx;
  font-weight: 600;
  color: #333333;
}

.title-placeholder {
  width: 72rpx;
}

.chat-area {
  flex: 1;
  min-height: 0;
  background-color: #F2F3F5;
}

.msg-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  padding: 20rpx 24rpx 0;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}

.msg-row--left {
  flex-direction: row;
}

.msg-row--right {
  flex-direction: row;
  justify-content: flex-end;
}

.avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  flex-shrink: 0;
  background-color: #E5E5E5;
}

.bubble {
  max-width: 420rpx;
  padding: 18rpx 24rpx;
  border-radius: 16rpx;
  position: relative;
  word-break: break-all;
}

.bubble-text {
  font-size: 28rpx;
  line-height: 1.5;
}

.bubble--other {
  background-color: #FFE8CC;
}

.bubble--other::before {
  content: '';
  position: absolute;
  left: -14rpx;
  top: 24rpx;
  width: 0;
  height: 0;
  border-width: 10rpx 16rpx 10rpx 0;
  border-style: solid;
  border-color: transparent #FFE8CC transparent transparent;
}

.bubble--self {
  background-color: #D4F5C0;
}

.bubble--self::before {
  content: '';
  position: absolute;
  right: -14rpx;
  top: 24rpx;
  width: 0;
  height: 0;
  border-width: 10rpx 0 10rpx 16rpx;
  border-style: solid;
  border-color: transparent transparent transparent #D4F5C0;
}

.input-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  padding-bottom: calc(16rpx + env(safe-area-inset-bottom));
  background-color: #FFFFFF;
  box-shadow: 0 -2rpx 8rpx rgba(0, 0, 0, 0.05);
  position: fixed;
  left: 0;
  right: 0;
  z-index: 10;
}

.input-box {
  flex: 1;
  height: 72rpx;
  background-color: #F5F5F5;
  border-radius: 36rpx;
  padding: 0 28rpx;
  font-size: 28rpx;
  color: #333333;
}

.send-btn {
  width: 120rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5B8DEF, #3B6FD4);
  color: #FFFFFF;
  font-size: 28rpx;
  border-radius: 36rpx;
  border: none;
  padding: 0;
  line-height: 1;
}

.send-btn[disabled] {
  opacity: 0.4;
}

.send-btn::after {
  border: none;
}
</style>

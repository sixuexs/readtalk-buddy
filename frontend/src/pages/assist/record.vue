<template>
  <view class="page-container">
    <!-- ===== 知情同意弹层（录音前强制确认） ===== -->
    <view v-if="!consented" class="consent-mask">
      <view class="consent-card">
        <text class="consent-title">录音前请确认</text>
        <view class="consent-body">
          <text class="consent-item">1. 请确保在场人员已知情并同意录音；</text>
          <text class="consent-item">2. 原始音频<b>不会被保存</b>，转写完成后即释放；</text>
          <text class="consent-item">3. 只有转写文本用于生成复盘报告，声纹不会被留存；</text>
          <text class="consent-item">4. 你可以在提交前删除任何不希望保留的内容。</text>
        </view>
        <view class="consent-btn" @tap="consented = true">
          <text class="consent-btn-text">我已知情并同意</text>
        </view>
        <view class="consent-cancel" @tap="goBack">
          <text class="consent-cancel-text">暂不录音</text>
        </view>
      </view>
    </view>

    <scroll-view v-else class="body-scroll" scroll-y>
      <!-- ===== 场景选择 ===== -->
      <view class="card">
        <text class="card-title">沟通场景</text>
        <view class="scene-row">
          <view
            class="scene-item"
            :class="{ 'scene-item--active': sceneType === 'one_on_one' }"
            @tap="setScene('one_on_one')"
          >
            <text class="scene-name">1 对 1</text>
            <text class="scene-desc">与一位书友交流</text>
          </view>
          <view
            class="scene-item"
            :class="{ 'scene-item--active': sceneType === 'salon' }"
            @tap="setScene('salon')"
          >
            <text class="scene-name">多人沙龙</text>
            <text class="scene-desc">读书会 / 小组讨论</text>
          </view>
        </view>
      </view>

      <!-- ===== 1 对 1：绑定书友 ===== -->
      <view v-if="sceneType === 'one_on_one'" class="card">
        <text class="card-title">这次是和谁（可选）</text>
        <text class="card-hint">绑定后会计入该书友的亲密度</text>
        <scroll-view class="chip-scroll" scroll-x>
          <view class="chip-row">
            <view
              class="chip"
              :class="{ 'chip--active': relatedContactId === '' }"
              @tap="relatedContactId = ''"
            >
              <text class="chip-text">暂不绑定</text>
            </view>
            <view
              v-for="c in contacts"
              :key="c.id"
              class="chip"
              :class="{ 'chip--active': relatedContactId === c.id }"
              @tap="relatedContactId = c.id"
            >
              <text class="chip-text">{{ c.name }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <!-- ===== 隐私开关 ===== -->
      <view class="card">
        <view class="switch-row">
          <view class="switch-info">
            <text class="switch-title">仅录我本人</text>
            <text class="switch-desc">
              开启后复盘只使用我的发言，他人发言不落库、不计入评分与亲密度；音频仍需整体送转写
            </text>
          </view>
          <switch :checked="onlyMe" color="#5b8def" @change="onOnlyMeChange" />
        </view>
      </view>

      <!-- ===== 录音区 ===== -->
      <view class="record-card">
        <text class="record-timer">{{ formatDuration(seconds) }}</text>
        <text class="record-state">{{ stateText }}</text>

        <view class="record-progress">
          <view
            class="record-progress__fill"
            :class="{ 'record-progress__fill--warn': progressPercent >= 90 }"
            :style="{ width: progressPercent + '%' }"
          />
        </view>

        <view
          class="record-btn"
          :class="{ 'record-btn--active': recording, 'record-btn--disabled': uploading }"
          @tap="toggleRecord"
        >
          <text class="record-btn-text">{{ recording ? '停止并转写' : '开始录音' }}</text>
        </view>

        <view v-if="recording" class="record-cancel" @tap="cancelRecord">
          <text class="record-cancel-text">取消本次录音</text>
        </view>

        <text class="record-tip">
          同步识别单段上限 5 分钟；请用手机靠近说话人收音，嘈杂环境识别率会下降
        </text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { startRecording, MAX_RECORD_SECONDS, formatDuration, type RecorderHandle } from '@/utils/recorder'
import { transcribeAudio } from '@/api/assist'
import { setTranscript } from '@/store/assist'
import { getRelationGraph } from '@/api/relation'
import type { GraphContact } from '@/types/relationGraph'
import type { AssistSceneType } from '@/types/assist'

const consented = ref(false)
const sceneType = ref<AssistSceneType>('one_on_one')
const onlyMe = ref(false)
const recording = ref(false)
const uploading = ref(false)
const seconds = ref(0)
const contacts = ref<GraphContact[]>([])
const relatedContactId = ref('')
let handle: RecorderHandle | null = null

const progressPercent = computed(() =>
  Math.min(100, Math.round((seconds.value / MAX_RECORD_SECONDS) * 100)),
)

const stateText = computed(() => {
  if (uploading.value) return 'AI 转写中，请稍候…'
  if (recording.value) {
    const left = MAX_RECORD_SECONDS - seconds.value
    return left <= 30 ? `即将到达上限，剩余 ${left} 秒` : '正在录音…'
  }
  return '点击开始录音'
})

onLoad(async () => {
  try {
    const res = await getRelationGraph()
    if (res.code === 0 && res.data?.contacts) {
      contacts.value = res.data.contacts.filter((c) => c.name)
    }
  } catch {
    // 联系人拉取失败不影响录音主链路
    contacts.value = []
  }
})

onUnload(() => {
  if (recording.value) {
    handle?.cancel()
    recording.value = false
  }
})

function setScene(v: AssistSceneType) {
  if (recording.value) return
  sceneType.value = v
}

function onOnlyMeChange(e: any) {
  onlyMe.value = !!e?.detail?.value
}

function toggleRecord() {
  if (uploading.value) return
  if (recording.value) {
    handle?.stop()
    return
  }
  beginRecord()
}

function beginRecord() {
  seconds.value = 0
  recording.value = true
  handle = startRecording({
    onTick: (s) => {
      seconds.value = s
    },
    onStop: (path) => {
      recording.value = false
      doTranscribe(path)
    },
    onError: (msg) => {
      recording.value = false
      uni.showToast({ title: msg, icon: 'none' })
    },
  })
}

function cancelRecord() {
  handle?.cancel()
  recording.value = false
  seconds.value = 0
}

async function doTranscribe(tempFilePath: string) {
  if (!tempFilePath) {
    uni.showToast({ title: '录音文件获取失败，请重试', icon: 'none' })
    return
  }
  uploading.value = true
  uni.showLoading({ title: 'AI 转写中…', mask: true })
  try {
    const res = await transcribeAudio(tempFilePath, 'mp3', 16000)
    if (res.code === 0 && res.data?.sentences?.length) {
      setTranscript({
        sentences: res.data.sentences,
        speakers: res.data.speakers || [],
        mock: !!res.data.mock,
        onlyMe: onlyMe.value,
        sceneType: sceneType.value,
      })
      const query = relatedContactId.value ? `?contactId=${relatedContactId.value}` : ''
      uni.navigateTo({ url: `/pages/assist/confirm${query}` })
    } else {
      uni.showToast({ title: '未识别到有效对话，请重试', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '转写失败，请检查网络后重试', icon: 'none' })
  } finally {
    uploading.value = false
    uni.hideLoading()
  }
}

function goBack() {
  uni.navigateBack({
    fail: () => {
      uni.switchTab({ url: '/pages/home/home' })
    },
  })
}
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f0f0f0;
  display: flex;
  flex-direction: column;
}

/* ===== 知情同意 ===== */
.consent-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 48rpx;
}
.consent-card {
  width: 100%;
  background: #fff;
  border-radius: 20rpx;
  padding: 40rpx 36rpx 28rpx;
  box-sizing: border-box;
}
.consent-title {
  font-size: 34rpx;
  font-weight: 600;
  color: #333;
  display: block;
  margin-bottom: 24rpx;
}
.consent-body {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-bottom: 32rpx;
}
.consent-item {
  font-size: 26rpx;
  color: #666;
  line-height: 1.6;
}
.consent-btn {
  height: 88rpx;
  border-radius: 44rpx;
  background: linear-gradient(135deg, #5b8def, #3b6fd4);
  display: flex;
  align-items: center;
  justify-content: center;
}
.consent-btn-text {
  font-size: 30rpx;
  color: #fff;
  font-weight: 600;
}
.consent-cancel {
  padding: 24rpx 0 8rpx;
  display: flex;
  justify-content: center;
}
.consent-cancel-text {
  font-size: 26rpx;
  color: #999;
}

/* ===== 通用卡片 ===== */
.body-scroll {
  flex: 1;
  min-height: 0;
  padding: 20rpx 0 60rpx;
}
.card {
  margin: 16rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  padding: 26rpx 28rpx;
}
.card-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
  display: block;
}
.card-hint {
  font-size: 22rpx;
  color: #999;
  display: block;
  margin-top: 8rpx;
}

/* ===== 场景 ===== */
.scene-row {
  display: flex;
  gap: 20rpx;
  margin-top: 20rpx;
}
.scene-item {
  flex: 1;
  border: 2rpx solid #e8e8e8;
  border-radius: 14rpx;
  padding: 22rpx 18rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  align-items: center;
}
.scene-item--active {
  border-color: #5b8def;
  background: #ebf0ff;
}
.scene-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
}
.scene-item--active .scene-name {
  color: #3b6fd4;
}
.scene-desc {
  font-size: 22rpx;
  color: #999;
}

/* ===== 书友 chips ===== */
.chip-scroll {
  margin-top: 20rpx;
  white-space: nowrap;
}
.chip-row {
  display: inline-flex;
  gap: 16rpx;
}
.chip {
  padding: 12rpx 28rpx;
  border-radius: 30rpx;
  background: #f3f4f6;
  border: 2rpx solid transparent;
}
.chip--active {
  background: #ebf0ff;
  border-color: #5b8def;
}
.chip-text {
  font-size: 26rpx;
  color: #555;
}
.chip--active .chip-text {
  color: #3b6fd4;
  font-weight: 500;
}

/* ===== 隐私开关 ===== */
.switch-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.switch-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.switch-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
}
.switch-desc {
  font-size: 22rpx;
  color: #999;
  line-height: 1.5;
}

/* ===== 录音区 ===== */
.record-card {
  margin: 16rpx 24rpx 40rpx;
  background: #fff;
  border-radius: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.06);
  padding: 44rpx 32rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18rpx;
}
.record-timer {
  font-size: 76rpx;
  font-weight: 700;
  color: #2b4a8f;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.record-state {
  font-size: 26rpx;
  color: #999;
}
.record-progress {
  width: 100%;
  height: 12rpx;
  background: #f0f0f0;
  border-radius: 6rpx;
  overflow: hidden;
  margin-top: 8rpx;
}
.record-progress__fill {
  height: 100%;
  border-radius: 6rpx;
  background: linear-gradient(90deg, #5b8def, #3b6fd4);
  transition: width 0.3s linear;
}
.record-progress__fill--warn {
  background: linear-gradient(90deg, #f59e0b, #f43f5e);
}
.record-btn {
  width: 240rpx;
  height: 240rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #5b8def, #3b6fd4);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 12rpx;
  box-shadow: 0 10rpx 30rpx rgba(59, 111, 212, 0.35);
}
.record-btn--active {
  background: linear-gradient(135deg, #f43f5e, #d92d4a);
  box-shadow: 0 10rpx 30rpx rgba(217, 45, 74, 0.35);
}
.record-btn--disabled {
  opacity: 0.55;
}
.record-btn-text {
  font-size: 30rpx;
  color: #fff;
  font-weight: 600;
  letter-spacing: 2rpx;
}
.record-cancel {
  padding: 8rpx 0;
}
.record-cancel-text {
  font-size: 24rpx;
  color: #f43f5e;
}
.record-tip {
  font-size: 22rpx;
  color: #999;
  text-align: center;
  line-height: 1.5;
  margin-top: 4rpx;
}
</style>

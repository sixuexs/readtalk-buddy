<template>
  <view v-if="visible" class="pop-mask" @tap="close">
    <view class="pop-panel" @tap.stop>
      <view class="pop-head">
        <text class="pop-title">打卡复盘 · {{ task?.title }}</text>
        <text class="pop-close" @tap="close">✕</text>
      </view>
      <text class="pop-hint">写下真实发生了什么：谁、什么情境、你说/做了什么、结果如何（≥10 字）</text>
      <textarea
        class="pop-input"
        v-model="text"
        placeholder="例如：同事说方案被驳回，我回了一句「听起来你为这个方案熬了不少夜，挺失落的吧」，她愣了一下说是的……"
        placeholder-style="color:#c0c4cc"
        :maxlength="200"
        auto-height
      />
      <view class="pop-count">
        <text class="pop-count-text">{{ text.trim().length }}/200</text>
        <text v-if="remaining !== null" class="pop-remain">剩余提交次数：{{ remaining }}</text>
      </view>

      <view v-if="result" class="pop-result" :class="result.accepted ? 'pop-result--ok' : 'pop-result--no'">
        <text class="pop-result-title">{{ result.accepted ? '✓ 教练点评：通过' : '△ 教练点评：未通过' }}</text>
        <text class="pop-result-text">{{ result.aiFeedback }}</text>
      </view>

      <view class="pop-submit" :class="{ 'pop-submit--disabled': submitting }" @tap="handleSubmit">
        <text class="pop-submit-text">{{ submitting ? '教练点评中…' : '提交打卡' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { PlanTask } from '@/types/plan'
import { submitPlanTask } from '@/api/plan'

const props = defineProps<{ visible: boolean; task: PlanTask | null }>()
const emit = defineEmits<{ (e: 'close'): void; (e: 'submitted'): void }>()

const text = ref('')
const submitting = ref(false)
const remaining = ref<number | null>(null)
const result = ref<{ accepted: boolean; aiFeedback: string } | null>(null)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      text.value = ''
      result.value = null
      submitting.value = false
      remaining.value = 3 - (props.task?.submissions?.length ?? 0)
    }
  },
)

async function handleSubmit() {
  if (!props.task || submitting.value) return
  const t = text.value.trim()
  if (t.length < 10) {
    uni.showToast({ title: '至少写 10 个字', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const res = await submitPlanTask(props.task.id, t)
    if (res.code === 0) {
      result.value = { accepted: res.data.accepted, aiFeedback: res.data.aiFeedback }
      remaining.value = res.data.remaining
      if (res.data.accepted) {
        emit('submitted')
      }
    } else {
      uni.showToast({ title: (res.data as any)?.message || '提交失败', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '网络异常，请重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

function close() {
  emit('close')
}
</script>

<style scoped>
.pop-mask {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}
.pop-panel {
  width: 100%;
  background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  padding: 28rpx 32rpx 48rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.pop-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pop-title {
  font-size: 29rpx;
  font-weight: 600;
  color: #333;
  flex: 1;
}
.pop-close {
  font-size: 30rpx;
  color: #999;
  padding: 6rpx 10rpx;
}
.pop-hint {
  font-size: 22rpx;
  color: #8a94a6;
  line-height: 1.5;
}
.pop-input {
  width: 100%;
  min-height: 160rpx;
  background: #f7f8fa;
  border-radius: 14rpx;
  padding: 20rpx;
  font-size: 27rpx;
  color: #333;
  box-sizing: border-box;
}
.pop-count {
  display: flex;
  justify-content: space-between;
}
.pop-count-text,
.pop-remain {
  font-size: 21rpx;
  color: #b0b8c4;
}
.pop-result {
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.pop-result--ok {
  background: #f0fbf5;
}
.pop-result--no {
  background: #fff7e8;
}
.pop-result-title {
  font-size: 24rpx;
  font-weight: 600;
}
.pop-result--ok .pop-result-title {
  color: #2f855a;
}
.pop-result--no .pop-result-title {
  color: #b45309;
}
.pop-result-text {
  font-size: 24rpx;
  color: #555;
  line-height: 1.5;
}
.pop-submit {
  height: 84rpx;
  border-radius: 42rpx;
  background: linear-gradient(135deg, #f59e0b, #d97706);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 6rpx;
}
.pop-submit--disabled {
  opacity: 0.6;
}
.pop-submit-text {
  color: #fff;
  font-size: 28rpx;
  font-weight: 600;
}
</style>

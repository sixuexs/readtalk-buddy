<template>
  <view class="task-card" :class="{ 'task-card--done': task.status === 'done', 'task-card--expired': task.status === 'expired' }">
    <view class="task-head">
      <text class="task-type-icon">{{ TYPE_ICON[task.type] }}</text>
      <view class="task-title-wrap">
        <text class="task-title">{{ task.title }}</text>
        <view class="task-badges">
          <text class="task-subskill">{{ subSkillCn(task.subSkill) }}</text>
          <text v-if="task.status === 'done'" class="task-status task-status--done">✓ 已完成</text>
          <text v-else-if="task.status === 'expired'" class="task-status task-status--expired">已过期</text>
        </view>
      </view>
    </view>

    <text v-if="task.successCriteria" class="task-criteria">达标：{{ task.successCriteria }}</text>

    <!-- 进度（auto 只读进度条，evidence 展示提交次数） -->
    <view class="task-progress-row">
      <view class="task-progress-bar">
        <view class="task-progress-fill" :class="{ 'task-progress-fill--done': task.progress >= task.target }"
          :style="{ width: Math.min(100, (task.progress / Math.max(1, task.target)) * 100) + '%' }" />
      </view>
      <text class="task-progress-text">{{ task.progress }}/{{ task.target }}</text>
    </view>

    <!-- 操作区 -->
    <view class="task-actions">
      <view v-if="task.verifyMode === 'auto' && task.status !== 'done' && task.status !== 'expired'"
        class="task-btn task-btn--train" @tap="$emit('train', task)">
        <text class="task-btn-text">🎬 去训练</text>
      </view>
      <view v-else-if="task.verifyMode === 'evidence' && task.status !== 'done' && task.status !== 'expired'"
        class="task-btn task-btn--submit" @tap="$emit('submit', task)">
        <text class="task-btn-text">✍️ 提交打卡</text>
      </view>
      <text class="task-how" @tap="howOpen = !howOpen">{{ howOpen ? '收起做法 ▴' : '怎么做 ▾' }}</text>
      <text v-if="task.whyRefs?.length" class="task-why" @tap="toggleWhy">为什么是这个任务 {{ whyOpen ? '▴' : '▾' }}</text>
    </view>

    <view v-if="howOpen" class="task-how-body">
      <text class="task-how-text">{{ task.instruction }}</text>
    </view>

    <view v-if="whyOpen" class="task-why-body">
      <view v-if="whyLoading" class="task-why-loading"><text class="task-why-text">加载证据…</text></view>
      <view v-else-if="!whyItems.length" class="task-why-loading"><text class="task-why-text">暂无关联证据</text></view>
      <view v-for="(w, i) in whyItems" :key="i" class="task-why-item">
        <text class="task-why-quote">“{{ w.quote }}”</text>
        <text v-if="w.issue" class="task-why-issue">{{ w.issue }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { PlanTask } from '@/types/plan'
import { subSkillCn } from '@/constants/subSkills'
import { getSubSkillEvidence } from '@/api/user'

const props = defineProps<{ task: PlanTask }>()
defineEmits<{ (e: 'train', task: PlanTask): void; (e: 'submit', task: PlanTask): void }>()

const TYPE_ICON: Record<string, string> = {
  simulation: '🎬',
  realworld: '🌍',
  knowledge: '📖',
}

const howOpen = ref(false)
const whyOpen = ref(false)
const whyLoading = ref(false)
const whyItems = ref<{ quote: string; issue: string }[]>([])

async function toggleWhy() {
  whyOpen.value = !whyOpen.value
  if (!whyOpen.value || whyItems.value.length || whyLoading.value) return
  whyLoading.value = true
  try {
    const res = await getSubSkillEvidence(props.task.subSkill, 20)
    const wanted = new Set((props.task.whyRefs ?? []).map((r) => r.sessionId))
    whyItems.value = (res.code === 0 ? res.data.items ?? [] : [])
      .filter((e) => wanted.has(e.sessionId ?? ''))
      .slice(0, 3)
      .map((e) => ({ quote: e.quote, issue: e.issue }))
  } catch {
    whyItems.value = []
  } finally {
    whyLoading.value = false
  }
}
</script>

<style scoped>
.task-card {
  background: #f8f9fb;
  border-radius: 14rpx;
  padding: 22rpx;
  margin-bottom: 16rpx;
  border: 2rpx solid #eef1f6;
}
.task-card--done {
  background: #f2fbf6;
  border-color: #c9ecd8;
}
.task-card--expired {
  opacity: 0.55;
}
.task-head {
  display: flex;
  gap: 12rpx;
  align-items: flex-start;
}
.task-type-icon {
  font-size: 32rpx;
  margin-top: 2rpx;
}
.task-title-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.task-title {
  font-size: 27rpx;
  font-weight: 600;
  color: #333;
  line-height: 1.4;
}
.task-badges {
  display: flex;
  align-items: center;
  gap: 10rpx;
  flex-wrap: wrap;
}
.task-subskill {
  font-size: 20rpx;
  color: #5b8def;
  background: #ebf0ff;
  padding: 2rpx 14rpx;
  border-radius: 16rpx;
}
.task-status {
  font-size: 20rpx;
  padding: 2rpx 14rpx;
  border-radius: 16rpx;
}
.task-status--done {
  color: #2f855a;
  background: #e8f8f0;
}
.task-status--expired {
  color: #999;
  background: #f0f0f0;
}
.task-criteria {
  font-size: 22rpx;
  color: #8a94a6;
  margin-top: 8rpx;
}
.task-progress-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 14rpx;
}
.task-progress-bar {
  flex: 1;
  height: 12rpx;
  background: #ececec;
  border-radius: 6rpx;
  overflow: hidden;
}
.task-progress-fill {
  height: 100%;
  border-radius: 6rpx;
  background: linear-gradient(90deg, #93c5fd, #5b8def);
  transition: width 0.3s ease;
}
.task-progress-fill--done {
  background: linear-gradient(90deg, #6ee7b7, #10b981);
}
.task-progress-text {
  font-size: 22rpx;
  color: #666;
  font-weight: 600;
}
.task-actions {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 16rpx;
}
.task-btn {
  padding: 10rpx 26rpx;
  border-radius: 30rpx;
}
.task-btn--train {
  background: linear-gradient(135deg, #5b8def, #3b6fd4);
}
.task-btn--submit {
  background: linear-gradient(135deg, #f59e0b, #d97706);
}
.task-btn:active {
  opacity: 0.85;
}
.task-btn-text {
  color: #fff;
  font-size: 23rpx;
  font-weight: 600;
}
.task-how,
.task-why {
  font-size: 22rpx;
  color: #5b8def;
}
.task-why {
  color: #999;
}
.task-how-body {
  margin-top: 12rpx;
  background: #fff;
  border-radius: 10rpx;
  padding: 16rpx;
}
.task-how-text {
  font-size: 23rpx;
  color: #555;
  line-height: 1.6;
}
.task-why-body {
  margin-top: 12rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.task-why-item {
  background: #fff;
  border-left: 5rpx solid #f59e23;
  border-radius: 8rpx;
  padding: 12rpx 16rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.task-why-quote {
  font-size: 23rpx;
  color: #444;
}
.task-why-issue {
  font-size: 21rpx;
  color: #8a6d3b;
}
.task-why-loading {
  padding: 6rpx;
}
.task-why-text {
  font-size: 21rpx;
  color: #b0b8c4;
}
</style>

<template>
  <view class="ev-card" :class="isPos ? 'ev-card--pos' : 'ev-card--neg'">
    <view class="ev-head">
      <text class="ev-tag" :class="isPos ? 'ev-tag--pos' : 'ev-tag--neg'">
        {{ isPos ? '✦ 高光时刻' : '△ 待改进' }} · {{ cnName }}
      </text>
    </view>
    <text class="ev-quote">“{{ item.quote }}”</text>
    <text v-if="item.issue" class="ev-issue">{{ item.issue }}</text>
    <view v-if="item.rewrite" class="ev-rewrite">
      <text class="ev-rewrite-label">换这样说</text>
      <text class="ev-rewrite-text">{{ item.rewrite }}</text>
    </view>
    <view v-if="showLink" class="ev-foot" @tap="goSession">
      <text class="ev-foot-text">{{ item.theme || '该场训练' }} · 查看完整对话 ›</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EvidenceItem } from '@/types/simulation'
import { subSkillCn } from '@/constants/subSkills'

const props = defineProps<{ item: EvidenceItem; hideLink?: boolean }>()

const isPos = computed(() => props.item.polarity === 'positive')
const cnName = computed(() => subSkillCn(props.item.subSkill))
const showLink = computed(() => !props.hideLink && !!props.item.sessionId)

function goSession() {
  if (!props.item.sessionId) return
  uni.navigateTo({ url: `/pages/social/chat-history?sessionId=${props.item.sessionId}` })
}
</script>

<style scoped>
.ev-card {
  background: #fafafa;
  border-radius: 12rpx;
  border-left: 6rpx solid #f59e23;
  padding: 20rpx 24rpx;
  margin-bottom: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}
.ev-card--pos {
  border-left-color: #43b581;
  background: #f4fbf7;
}
.ev-head {
  display: flex;
}
.ev-tag {
  font-size: 22rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  color: #b45309;
  background: #fff6e5;
}
.ev-tag--pos {
  color: #2f855a;
  background: #e8f8f0;
}
.ev-quote {
  font-size: 27rpx;
  color: #333;
  line-height: 1.6;
  font-weight: 500;
}
.ev-issue {
  font-size: 24rpx;
  color: #8a6d3b;
  line-height: 1.5;
}
.ev-rewrite {
  background: #eef4ff;
  border-radius: 10rpx;
  padding: 14rpx 18rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.ev-rewrite-label {
  font-size: 20rpx;
  color: #5b8def;
  font-weight: 600;
}
.ev-rewrite-text {
  font-size: 25rpx;
  color: #33475b;
  line-height: 1.6;
}
.ev-foot {
  display: flex;
  justify-content: flex-end;
}
.ev-foot-text {
  font-size: 22rpx;
  color: #5b8def;
}
</style>

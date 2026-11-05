<template>
  <view class="badge-wall">
    <view
      v-for="s in SUB_SKILLS"
      :key="s.key"
      class="badge-cell"
      :class="cellClass(s.key)"
    >
      <text class="badge-icon">{{ cellIcon(s.key) }}</text>
      <text class="badge-name">{{ s.cnName }}</text>
      <text class="badge-level">{{ cellLevelText(s.key) }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { SUB_SKILLS } from '@/constants/subSkills'
import type { BadgeView } from '@/types/plan'

const props = defineProps<{ badges?: BadgeView[] }>()

const badgeMap = computed(() => {
  const m = new Map<string, BadgeView>()
  for (const b of props.badges ?? []) {
    m.set(b.subSkill, b)
  }
  return m
})

function cellClass(key: string): string {
  const b = badgeMap.value.get(key)
  if (!b) return 'badge-cell--none'
  return b.level >= 2 ? 'badge-cell--lv2' : 'badge-cell--lv1'
}

function cellIcon(key: string): string {
  const b = badgeMap.value.get(key)
  return b ? (b.level >= 2 ? '🏆' : '🏅') : '·'
}

function cellLevelText(key: string): string {
  const b = badgeMap.value.get(key)
  if (!b) return '未达成'
  return b.level >= 2 ? '精通' : '掌握'
}
</script>

<style scoped>
.badge-wall {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.badge-cell {
  width: calc(25% - 9rpx);
  box-sizing: border-box;
  border-radius: 14rpx;
  padding: 16rpx 6rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}
.badge-cell--none {
  background: #f6f7f9;
  opacity: 0.6;
}
.badge-cell--lv1 {
  background: linear-gradient(135deg, #fff7e8, #ffefd4);
  border: 2rpx solid #ffd79a;
}
.badge-cell--lv2 {
  background: linear-gradient(135deg, #fffbe8, #ffedbb);
  border: 2rpx solid #f5b93f;
}
.badge-icon {
  font-size: 34rpx;
}
.badge-name {
  font-size: 20rpx;
  color: #444;
  text-align: center;
}
.badge-level {
  font-size: 18rpx;
  color: #999;
}
</style>

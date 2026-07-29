<template>
  <scroll-view class="contact-list" scroll-y>
    <view v-if="!contacts.length" class="list-empty">
      <text class="list-empty-text">暂无联系人</text>
    </view>

    <view
      v-for="contact in contacts"
      :key="contact.id"
      class="list-item"
      @tap="$emit('select', contact)"
    >
      <view class="item-avatar" :style="{ backgroundColor: avatarBg(contact.id) }">
        <image
          v-if="contact.avatarUrl"
          class="item-avatar-img"
          :src="contact.avatarUrl"
          mode="aspectFill"
        />
        <text v-else class="item-avatar-text">{{ contact.name.charAt(0) }}</text>
        <!-- 预警角标 -->
        <view
          v-if="warningOf(contact.id)"
          class="item-badge"
          :style="{ backgroundColor: badgeColor(contact.id) }"
        />
      </view>

      <view class="item-info">
        <text class="item-name">{{ contact.name }}</text>
        <text class="item-relation">{{ contact.relationType }}</text>
      </view>

      <view class="item-right">
        <text class="item-intimacy">{{ Math.round(contact.intimacyScore) }}</text>
        <text class="item-intimacy-label">亲密度</text>
      </view>
    </view>
  </scroll-view>
</template>

<script setup lang="ts">
import type { GraphContact, GraphWarning } from '@/types/relationGraph'

const props = defineProps<{
  contacts: GraphContact[]
  warnings: GraphWarning[]
}>()

defineEmits<{
  (e: 'select', contact: GraphContact): void
}>()

const AVATAR_COLORS = ['#93C5FD', '#A7F3D0', '#FDE68A', '#FCA5A5', '#C4B5FD']

const LEVEL_COLORS: Record<string, string> = {
  YELLOW: '#FBBF24',
  ORANGE: '#F97316',
  RED: '#EF4444',
}

function avatarBg(id: number): string {
  return AVATAR_COLORS[id % AVATAR_COLORS.length]
}

function warningOf(contactId: number): GraphWarning | undefined {
  // 冷却中（已"暂不提醒"）不显示角标
  return props.warnings.find((w) => w.contactId === contactId && !w.dismissed)
}

function badgeColor(contactId: number): string {
  const w = warningOf(contactId)
  return w ? LEVEL_COLORS[w.level] || '#FBBF24' : 'transparent'
}
</script>

<style scoped>
.contact-list {
  height: 100%;
}

.list-empty {
  padding: 120rpx 0;
  display: flex;
  justify-content: center;
}

.list-empty-text {
  font-size: 26rpx;
  color: #9CA3AF;
}

.list-item {
  display: flex;
  align-items: center;
  background-color: #FFFFFF;
  border-radius: 20rpx;
  padding: 24rpx;
  margin: 0 24rpx 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.item-avatar {
  position: relative;
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
}

.item-avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
}

.item-avatar-text {
  font-size: 36rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.item-badge {
  position: absolute;
  top: 0;
  right: 0;
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  border: 4rpx solid #FFFFFF;
  box-sizing: border-box;
}

.item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.item-name {
  font-size: 30rpx;
  font-weight: 500;
  color: #1F2937;
  margin-bottom: 6rpx;
}

.item-relation {
  font-size: 24rpx;
  color: #9CA3AF;
}

.item-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.item-intimacy {
  font-size: 32rpx;
  font-weight: 600;
  color: #3B82F6;
}

.item-intimacy-label {
  font-size: 20rpx;
  color: #9CA3AF;
}
</style>

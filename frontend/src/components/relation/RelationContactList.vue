<template>
  <scroll-view
    class="contact-list"
    :class="{
      'contact-list--compact': compact,
      'contact-list--grid': grid,
    }"
    scroll-y
    :show-scrollbar="false"
  >
    <view v-if="!grouped.length && filterKeyword.trim()" class="contact-list__empty">
      <text>没有匹配的星友 ✨</text>
    </view>

    <view
      v-for="group in grouped"
      :key="group.type"
      class="contact-group"
    >
      <view class="contact-group__head">
        <view class="contact-group__title-wrap">
          <text class="contact-group__emoji">{{ relationIcon(group.type as RelationType) }}</text>
          <text
            class="contact-group__title"
            :style="{ color: getRelationPalette(group.type as RelationType).main }"
          >{{ relationLabel(group.type as RelationType) }}</text>
        </view>
        <text class="contact-group__count">{{ group.items.length }} 位星友</text>
      </view>

      <view class="contact-group__cards">
        <view
          v-for="person in group.items"
          :key="person.id"
        class="contact-card"
        :class="{ 'contact-card--active': person.id === selectedId }"
        :style="person.id === selectedId ? { borderColor: getRelationPalette(person.relationType).main } : {}"
        hover-class="contact-card--hover"
        @tap.stop="emit('select', person.id)"
      >
        <view
          class="contact-card__avatar-wrap"
          :style="{ background: getRelationPalette(person.relationType).gradient }"
        >
          <text class="contact-card__avatar-text">{{ person.name.slice(0, 1) }}</text>
        </view>
          <view class="contact-card__body">
            <view class="contact-card__row">
              <text class="contact-card__name">{{ person.name }}</text>
              <view class="intimacy-badge" :style="{ background: badgeColor(person.intimacy) }">
                <text class="intimacy-badge__text">💞 {{ person.intimacy }}</text>
              </view>
            </view>
            <text class="contact-card__meta">
              💬 {{ intimacyLabel(person.intimacy) }} · 🕐 {{ person.lastContactDays }} 天前聊过
            </text>
            <text v-if="person.note && !compact" class="contact-card__note">{{ person.note }}</text>
          </view>
          <view class="intimacy-bar">
          <view
            class="intimacy-bar__fill"
            :style="{
              width: person.intimacy + '%',
              background: barFillColor(person.intimacy),
            }"
          />
          </view>
        </view>
      </view>
    </view>
    <view class="contact-list__footer" />
  </scroll-view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RelationContact, RelationType } from '@/types/relation'
import {
  getRelationPalette,
  intimacyBadgeColor,
  intimacyBarColor,
} from '@/constants/freshTheme'
import { groupContactsByType, intimacyLabel } from '@/utils/relationGraph'
import { matchContactsByKeyword } from '@/utils/relationSearch'
import { RELATION_TYPE_ICON, relationTypeLabel } from '@/constants/relationCopy'

function relationIcon(type: RelationType) {
  return RELATION_TYPE_ICON[type]
}

function relationLabel(type: RelationType) {
  return relationTypeLabel(type)
}

const props = withDefaults(
  defineProps<{
    contacts: RelationContact[]
    selectedId?: string | null
    /** 侧栏紧凑样式 */
    compact?: boolean
    /** 宽屏通讯录多列网格 */
    grid?: boolean
    /** 搜索关键词过滤 */
    filterKeyword?: string
  }>(),
  {
    selectedId: null,
    compact: false,
    grid: false,
    filterKeyword: '',
  },
)

const emit = defineEmits(['select'])

const filteredContacts = computed(() =>
  matchContactsByKeyword(props.contacts, props.filterKeyword),
)

const grouped = computed(() => groupContactsByType(filteredContacts.value))

function badgeColor(intimacy: number): string {
  return intimacyBadgeColor(intimacy)
}

/** 进度条填充（小程序对 linear-gradient 支持不一，高亲密度用纯色） */
function barFillColor(intimacy: number): string {
  const g = intimacyBarColor(intimacy)
  return g.includes('gradient') ? intimacyBadgeColor(intimacy) : g
}
</script>

<style scoped>
.contact-list {
  flex: 1;
  height: 100%;
  min-height: 0;
}

.contact-group {
  margin-bottom: 8rpx;
}

.contact-group__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16rpx 8rpx 12rpx;
}

.contact-group__title-wrap {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.contact-group__emoji {
  font-size: 28rpx;
  line-height: 1;
}

.contact-group__title {
  font-size: 26rpx;
  font-weight: 600;
  letter-spacing: 1rpx;
}

.contact-group__count {
  font-size: 22rpx;
  color: #aaa;
}

.contact-group__cards {
  display: flex;
  flex-direction: column;
}

.contact-card {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 20rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  background: #fff;
  border-radius: 16rpx;
  border: 1rpx solid rgba(255, 126, 179, 0.12);
  position: relative;
  overflow: hidden;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
  box-sizing: border-box;
}

.contact-card--active {
  box-shadow: 0 6rpx 24rpx rgba(59, 158, 255, 0.15);
  background: linear-gradient(135deg, #fff, #f8fbff);
}

.contact-card--hover {
  background: #fff8fc;
  transform: translateY(-2rpx);
}

.contact-card__avatar-wrap {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 6rpx 16rpx rgba(0, 0, 0, 0.1);
}

.contact-card__avatar-text {
  font-size: 36rpx;
  font-weight: 700;
  color: #fff;
}

.contact-card__body {
  flex: 1;
  min-width: 0;
}

.contact-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 8rpx;
}

.contact-card__name {
  font-size: 30rpx;
  font-weight: 600;
  color: #2a3441;
}

.intimacy-badge {
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  flex-shrink: 0;
}

.intimacy-badge__text {
  font-size: 22rpx;
  font-weight: 600;
  color: #fff;
}

.contact-card__meta {
  font-size: 24rpx;
  color: #5c6b7a;
  display: block;
}

.contact-card__note {
  font-size: 22rpx;
  color: #aaa;
  margin-top: 8rpx;
  display: block;
}

.intimacy-bar {
  width: 100%;
  height: 6rpx;
  background: #e6eef3;
  border-radius: 3rpx;
  margin-top: 4rpx;
  overflow: hidden;
}

.intimacy-bar__fill {
  height: 100%;
  border-radius: 3rpx;
  transition: width 0.35s ease;
}

.contact-list__footer {
  height: 24rpx;
}

.contact-list__empty {
  padding: 48rpx 24rpx;
  text-align: center;
}

.contact-list__empty text {
  font-size: 26rpx;
  color: #b0bec5;
}

/* 侧栏紧凑 */
.contact-list--compact .contact-group__head {
  padding: 8px 4px 6px;
}

.contact-list--compact .contact-group__title {
  font-size: 13px;
}

.contact-list--compact .contact-group__count {
  font-size: 11px;
}

.contact-list--compact .contact-card {
  padding: 12px;
  margin-bottom: 8px;
  border-radius: 10px;
  box-shadow: none;
  border: 1rpx solid #e6eef3;
  background: #fff;
}

.contact-list--compact .contact-card__avatar-wrap {
  width: 36px;
  height: 36px;
}

.contact-list--compact .contact-card__avatar-text {
  font-size: 15px;
}

.contact-list--compact .contact-card__name {
  font-size: 14px;
}

.contact-list--compact .contact-card__meta {
  font-size: 11px;
}

.contact-list--compact .intimacy-badge__text {
  font-size: 11px;
}

.contact-list--compact .intimacy-badge {
  padding: 2px 8px;
}

/* 宽屏通讯录网格 */
@media (min-width: 768px) {
  .contact-list--grid .contact-group__cards {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 16px;
  }

  .contact-list--grid .contact-card {
    margin-bottom: 0;
    height: 100%;
  }

  .contact-list--grid .contact-group__head {
    padding: 12px 4px 10px;
  }

  .contact-list--grid .contact-group__title {
    font-size: 15px;
  }
}

@media (min-width: 1200px) {
  .contact-list--grid .contact-group__cards {
    grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  }
}
</style>

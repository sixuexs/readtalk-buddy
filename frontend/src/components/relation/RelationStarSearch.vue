<template>
  <view class="star-search" :class="{ 'star-search--compact': compact }">
    <view class="star-search__head">
      <text class="star-search__icon">{{ copy.icon }}</text>
      <text class="star-search__title">{{ copy.title }}</text>
    </view>

    <view class="star-search__box">
      <text class="star-search__lens">🔍</text>
      <input
        class="star-search__input"
        type="text"
        :value="props.keyword"
        :placeholder="copy.placeholder"
        placeholder-class="star-search__placeholder"
        confirm-type="search"
        @input="onInput"
        @confirm="onConfirm"
        @focus="focused = true"
        @blur="onBlur"
      />
      <view
        v-if="props.keyword"
        class="star-search__clear"
        @tap.stop="clearKeyword"
      >
        <text>✕</text>
      </view>
    </view>

    <view v-if="showDropdown" class="star-search__dropdown">
      <view
        v-for="item in matches"
        :key="item.id"
        class="star-search__result"
        hover-class="star-search__result--hover"
        @tap.stop="pick(item.id)"
      >
        <view
          class="star-search__avatar"
          :style="{ background: palette(item.relationType).gradient }"
        >
          <text>{{ item.name.slice(0, 1) }}</text>
        </view>
        <view class="star-search__info">
          <text class="star-search__name">{{ item.name }}</text>
          <text class="star-search__meta">
            {{ relationIcon(item.relationType) }} {{ item.relationType }} · 💞 {{ item.intimacy }}
          </text>
        </view>
      </view>
    </view>

    <text v-else-if="keyword.trim() && !matches.length" class="star-search__empty">
      {{ copy.noResult }}
    </text>
    <text v-else class="star-search__hint">{{ hintText }}</text>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { RelationContact, RelationType } from '@/types/relation'
import { getRelationPalette } from '@/constants/freshTheme'
import { RELATION_PAGE, RELATION_TYPE_ICON } from '@/constants/relationCopy'
import { matchContactsByKeyword } from '@/utils/relationSearch'

const props = withDefaults(
  defineProps<{
    contacts: RelationContact[]
    keyword?: string
    compact?: boolean
  }>(),
  {
    keyword: '',
    compact: false,
  },
)

const emit = defineEmits<{
  'update:keyword': [value: string]
  select: [id: string]
}>()

const copy = RELATION_PAGE.sidebarSearch
const focused = ref(false)
let blurTimer: ReturnType<typeof setTimeout> | null = null

const matches = computed(() =>
  matchContactsByKeyword(props.contacts, props.keyword),
)

const showDropdown = computed(
  () => focused.value && props.keyword.trim().length > 0 && matches.value.length > 0,
)

const hintText = computed(() => {
  if (props.keyword.trim() && matches.value.length) {
    return `找到 ${matches.value.length} 位星友，点选即可在星图定位`
  }
  return copy.hint
})

function palette(type: RelationType) {
  return getRelationPalette(type)
}

function relationIcon(type: RelationType) {
  return RELATION_TYPE_ICON[type]
}

function onInput(e: any) {
  const value = e?.detail?.value ?? ''
  emit('update:keyword', value)
}

function onBlur() {
  blurTimer = setTimeout(() => {
    focused.value = false
  }, 180)
}

function clearKeyword() {
  emit('update:keyword', '')
}

function pick(id: string) {
  if (blurTimer) clearTimeout(blurTimer)
  focused.value = false
  emit('select', id)
}

function onConfirm() {
  const first = matches.value[0]
  if (first) pick(first.id)
}
</script>

<style scoped>
.star-search {
  padding: 16rpx 20rpx 12rpx;
  flex-shrink: 0;
}

.star-search--compact {
  padding: 16px 16px 10px;
}

.star-search__head {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 16rpx;
}

.star-search__icon {
  font-size: 32rpx;
  line-height: 1;
}

.star-search__title {
  font-size: 30rpx;
  font-weight: 600;
  color: #2a3441;
}

.star-search__box {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 20rpx;
  height: 72rpx;
  background: #fff;
  border-radius: 36rpx;
  border: 1rpx solid rgba(59, 158, 255, 0.2);
  box-shadow: 0 4rpx 16rpx rgba(59, 158, 255, 0.08);
}

.star-search__lens {
  font-size: 28rpx;
  flex-shrink: 0;
  opacity: 0.85;
}

.star-search__input {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  font-size: 28rpx;
  color: #2a3441;
}

.star-search__placeholder {
  color: #b0bec5;
  font-size: 28rpx;
}

.star-search__clear {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: #f0f4f8;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.star-search__clear text {
  font-size: 22rpx;
  color: #8e9dab;
}

.star-search__dropdown {
  margin-top: 12rpx;
  background: #fff;
  border-radius: 16rpx;
  border: 1rpx solid rgba(59, 158, 255, 0.15);
  box-shadow: 0 4rpx 16rpx rgba(59, 158, 255, 0.1);
  max-height: 320rpx;
  overflow-y: auto;
}

.star-search__result {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  border-bottom: 1rpx solid #f5f0fa;
}

.star-search__result:last-child {
  border-bottom: none;
}

.star-search__result--hover {
  background: #fff8fc;
}

.star-search__avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.star-search__avatar text {
  font-size: 28rpx;
  font-weight: 700;
  color: #fff;
}

.star-search__info {
  flex: 1;
  min-width: 0;
}

.star-search__name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #2a3441;
}

.star-search__meta {
  display: block;
  font-size: 22rpx;
  color: #6b7c8d;
  margin-top: 6rpx;
}

.star-search__hint,
.star-search__empty {
  display: block;
  font-size: 22rpx;
  margin-top: 12rpx;
  line-height: 1.4;
}

.star-search__hint {
  color: #ff7eb3;
  font-weight: 500;
}

.star-search__empty {
  color: #b0bec5;
}

@media (min-width: 768px) {
  .star-search__head {
    margin-bottom: 10px;
  }

  .star-search__icon {
    font-size: 18px;
  }

  .star-search__title {
    font-size: 15px;
  }

  .star-search__box {
    height: 40px;
    border-radius: 20px;
    padding: 0 14px;
  }

  .star-search__input {
    height: 40px;
    font-size: 14px;
  }

  .star-search__placeholder {
    font-size: 14px;
  }

  .star-search__dropdown {
    margin-top: 8px;
    max-height: 200px;
    border-radius: 12px;
  }

  .star-search__result {
    padding: 10px 14px;
  }

  .star-search__avatar {
    width: 36px;
    height: 36px;
  }

  .star-search__avatar text {
    font-size: 15px;
  }

  .star-search__name {
    font-size: 14px;
  }

  .star-search__meta {
    font-size: 12px;
  }

  .star-search__hint,
  .star-search__empty {
    font-size: 12px;
    margin-top: 8px;
  }
}
</style>

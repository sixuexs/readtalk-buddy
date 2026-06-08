<template>
  <view
    v-if="show"
    class="person-drawer person-drawer--show"
    @tap.stop="handleClose"
  >
    <view class="person-drawer__panel" @tap.stop>
      <view
        class="person-drawer__header"
        :style="{ background: headerGradient }"
      >
        <view class="person-drawer__close" @tap.stop="handleClose">
          <text class="person-drawer__close-icon">✕</text>
        </view>
        <view
          class="person-drawer__avatar"
          :style="{ borderColor: avatarAccent }"
        >
          <text class="person-drawer__avatar-text" :style="{ color: avatarAccent }">
            {{ contact!.name.slice(0, 1) }}
          </text>
        </view>
        <text class="person-drawer__name">{{ contact!.name }}</text>
        <view class="person-drawer__relation-pill">
          <text>{{ relationIcon }} {{ contact!.relationType }}</text>
        </view>
      </view>

      <scroll-view class="person-drawer__body" scroll-y :show-scrollbar="false">
        <view class="info-block">
          <text class="info-block__title">{{ sections.intimacy }}</text>
          <view class="intimacy-row">
            <view class="intimacy-bar">
              <view
                class="intimacy-bar__fill"
                :style="{
                  width: contact!.intimacy + '%',
                  background: intimacyFillColor,
                }"
              />
            </view>
            <text class="intimacy-percent" :style="{ color: intimacyFillColor }">
              {{ contact!.intimacy }}%
            </text>
          </view>
          <text class="intimacy-desc">{{ intimacyDescription(contact!.intimacy) }}</text>
        </view>

        <view v-if="contact!.personality" class="info-block">
          <text class="info-block__title">{{ sections.personality }}</text>
          <text class="info-block__text">{{ contact!.personality }}</text>
        </view>

        <view v-if="contact!.interests?.length" class="info-block">
          <text class="info-block__title">{{ sections.interests }}</text>
          <view class="tag-row">
            <view
              v-for="(tag, idx) in contact!.interests"
              :key="'i-' + tag"
              class="tag"
              :style="{ background: tagStyle(idx).bg }"
            >
              <text :style="{ color: tagStyle(idx).color }">{{ tag }}</text>
            </view>
          </view>
        </view>

        <view v-if="displayLabels.length" class="info-block">
          <text class="info-block__title">{{ sections.labels }}</text>
          <view class="tag-row">
            <view
              v-for="(tag, idx) in displayLabels"
              :key="'l-' + tag"
              class="tag"
              :style="{ background: tagStyle(idx + interestOffset).bg }"
            >
              <text :style="{ color: tagStyle(idx + interestOffset).color }">{{ tag }}</text>
            </view>
          </view>
        </view>

        <view class="person-drawer__footer-space" />
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RelationContact } from '@/types/relation'
import { intimacyDescription } from '@/utils/relationGraph'
import {
  drawerHeaderGradient,
  getRelationPalette,
  intimacyBadgeColor,
  tagPaletteByIndex,
} from '@/constants/freshTheme'
import {
  DRAWER_SECTIONS,
  RELATION_TYPE_ICON,
} from '@/constants/relationCopy'

const sections = DRAWER_SECTIONS

const props = defineProps<{
  contact: RelationContact | null
  show: boolean
}>()

const emit = defineEmits(['close'])

const displayLabels = computed(() => {
  if (!props.contact) return []
  const labels = props.contact.labels ?? []
  if (labels.length) return labels
  return [props.contact.relationType]
})

const interestOffset = computed(() => props.contact?.interests?.length ?? 0)

const headerGradient = computed(() =>
  props.contact ? drawerHeaderGradient(props.contact.relationType) : '',
)

const avatarAccent = computed(() =>
  props.contact ? getRelationPalette(props.contact.relationType).main : '#6BB8D4',
)

const relationIcon = computed(() =>
  props.contact ? RELATION_TYPE_ICON[props.contact.relationType] : '✨',
)

const intimacyFillColor = computed(() =>
  props.contact ? intimacyBadgeColor(props.contact.intimacy) : '#88CCE5',
)

function tagStyle(index: number) {
  const p = tagPaletteByIndex(index)
  return { bg: p.bg, color: p.text }
}

function handleClose() {
  emit('close')
}
</script>

<style scoped>
.person-drawer {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(42, 52, 65, 0.45);
}

.person-drawer__panel {
  width: 100%;
  max-width: 420px;
  max-height: 88vh;
  background: linear-gradient(180deg, #fff8fc 0%, #f8fbff 100%);
  border-radius: 28rpx 28rpx 0 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -8rpx 40rpx rgba(255, 126, 179, 0.2);
}

.person-drawer__header {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 32rpx 40rpx;
}

.person-drawer__close {
  position: absolute;
  top: 24rpx;
  right: 24rpx;
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
}

.person-drawer__close-icon {
  font-size: 40rpx;
  color: #fff;
  line-height: 1;
  font-weight: 300;
}

.person-drawer__avatar {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.95);
  border: 6rpx solid rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.08);
}

.person-drawer__avatar-text {
  font-size: 56rpx;
  font-weight: 700;
}

.person-drawer__name {
  margin-top: 24rpx;
  font-size: 40rpx;
  font-weight: 700;
  color: #fff;
  letter-spacing: 2rpx;
}

.person-drawer__relation-pill {
  margin-top: 16rpx;
  padding: 8rpx 28rpx;
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.22);
  border: 1rpx solid rgba(255, 255, 255, 0.35);
}

.person-drawer__relation-pill text {
  font-size: 24rpx;
  color: #fff;
}

.person-drawer__body {
  flex: 1;
  min-height: 0;
  max-height: 52vh;
  padding: 24rpx 28rpx 32rpx;
  box-sizing: border-box;
}

.info-block {
  background: #fff;
  border-radius: 16rpx;
  padding: 28rpx;
  margin-bottom: 16rpx;
  border: 1rpx solid rgba(255, 126, 179, 0.12);
  box-shadow: 0 4rpx 16rpx rgba(59, 158, 255, 0.06);
}

.info-block__title {
  display: block;
  font-size: 26rpx;
  color: #5c6b7a;
  font-weight: 600;
  margin-bottom: 20rpx;
}

.info-block__text {
  font-size: 30rpx;
  color: #2a3441;
  line-height: 1.5;
}

.intimacy-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.intimacy-bar {
  flex: 1;
  height: 16rpx;
  background: #ffe8f2;
  border-radius: 8rpx;
  overflow: hidden;
}

.intimacy-bar__fill {
  height: 100%;
  border-radius: 8rpx;
  transition: width 0.4s ease;
}

.intimacy-percent {
  font-size: 30rpx;
  font-weight: 600;
  flex-shrink: 0;
  min-width: 72rpx;
  text-align: right;
}

.intimacy-desc {
  display: block;
  margin-top: 16rpx;
  font-size: 26rpx;
  color: #ff7eb3;
  font-weight: 500;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.tag {
  padding: 12rpx 24rpx;
  border-radius: 12rpx;
}

.tag text {
  font-size: 26rpx;
}

.person-drawer__footer-space {
  height: 24rpx;
}

@media (min-width: 768px) {
  .person-drawer {
    align-items: center;
    padding: 24px;
  }

  .person-drawer__panel {
    max-width: 400px;
    max-height: 85vh;
    border-radius: 20px;
  }

  .person-drawer__header {
    padding: 32px 24px 28px;
  }

  .person-drawer__avatar {
    width: 88px;
    height: 88px;
  }

  .person-drawer__avatar-text {
    font-size: 36px;
  }

  .person-drawer__name {
    font-size: 22px;
    margin-top: 14px;
  }

  .person-drawer__body {
    max-height: 60vh;
    padding: 16px 20px 20px;
  }

  .info-block {
    padding: 18px;
    margin-bottom: 12px;
    border-radius: 12px;
  }

  .info-block__title {
    font-size: 13px;
    margin-bottom: 12px;
  }

  .info-block__text {
    font-size: 15px;
  }

  .intimacy-percent {
    font-size: 15px;
  }

  .intimacy-desc {
    font-size: 13px;
  }

  .tag text {
    font-size: 13px;
  }
}
</style>

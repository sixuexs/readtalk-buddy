<template>
  <view id="relation-insight" class="insight-section">
    <view
      v-if="showQuery"
      class="query-bar"
      @tap.stop="openAnalysis"
    >
      <view class="query-bar__left">
        <view class="query-bar__icon">
          <text class="query-bar__icon-text">问</text>
        </view>
        <text class="query-bar__text">{{ queryText }}</text>
      </view>
      <text class="query-bar__action">立即查看 ›</text>
    </view>

    <view class="insight-card">
      <view class="insight-card__head">
        <view class="insight-tabs">
          <view
            v-for="tab in tabs"
            :key="tab.id"
            class="insight-tab"
            :class="{ 'insight-tab--active': activeTab === tab.id }"
            @tap.stop="activeTab = tab.id"
          >
            <text class="insight-tab__emoji">{{ tab.icon }}</text>
            <text class="insight-tab__label">{{ tab.label }}</text>
            <view
              v-if="activeTab === tab.id"
              class="insight-tab__line"
              :style="{ background: tab.color }"
            />
          </view>
        </view>
        <view class="insight-brand">
          <view class="insight-brand__dots">
            <view class="insight-brand__dot insight-brand__dot--green" />
            <view class="insight-brand__dot insight-brand__dot--yellow" />
            <view class="insight-brand__dot insight-brand__dot--blue" />
          </view>
          <text class="insight-brand__name">智伴</text>
        </view>
      </view>

      <text class="insight-card__disclaimer">
        内容由阅谈智伴根据当前关系数据生成，仅供参考
      </text>

      <!-- 关系分析 -->
      <view v-if="activeTab === 'analysis' && focus" class="insight-body">
        <view class="focus-card">
          <view class="focus-card__hero" :style="{ background: heroGradient }">
            <view class="focus-card__avatar" :style="{ borderColor: relationMain + '40' }">
              <text :style="{ color: relationMain }">{{ focus.name.slice(0, 1) }}</text>
            </view>
            <view class="focus-card__hero-text">
              <text class="focus-card__name">聚焦：{{ focus.name }}</text>
              <view
                class="focus-card__relation-pill"
                :style="{ background: relationLight, borderColor: relationMain + '30' }"
              >
                <text :style="{ color: relationMain }">{{ relationIcon }} {{ focus.relationType }}</text>
              </view>
            </view>
            <view class="focus-card__score">
              <text class="focus-card__score-num">{{ focus.intimacy }}</text>
              <text class="focus-card__score-unit">%</text>
            </view>
          </view>

          <view class="focus-card__body">
            <!-- 亲密度 -->
            <view class="info-row info-row--pink">
              <text class="info-row__icon">💞</text>
              <view class="info-row__main">
                <text class="info-row__label">亲密度</text>
                <view class="intimacy-track">
                  <view
                    class="intimacy-track__fill"
                    :style="{
                      width: focus.intimacy + '%',
                      background: intimacyColor,
                    }"
                  />
                </view>
                <text class="info-row__desc">{{ intimacyDesc }}</text>
              </view>
            </view>

            <!-- 标签 -->
            <view class="info-row info-row--blue">
              <text class="info-row__icon">🏷️</text>
              <view class="info-row__main">
                <text class="info-row__label">身份标签</text>
                <view class="tag-row">
                  <view
                    v-for="(tag, idx) in displayLabels"
                    :key="tag"
                    class="tag-pill"
                    :style="tagStyle(idx)"
                  >
                    <text>{{ tag }}</text>
                  </view>
                </view>
              </view>
            </view>

            <!-- 性格 -->
            <view v-if="focus.personality" class="info-row info-row--purple">
              <text class="info-row__icon">🎭</text>
              <view class="info-row__main">
                <text class="info-row__label">性格速写</text>
                <text class="info-row__value">{{ focus.personality }}</text>
              </view>
            </view>

            <!-- 兴趣 -->
            <view v-if="focus.interests?.length" class="info-row info-row--mint">
              <text class="info-row__icon">🎯</text>
              <view class="info-row__main">
                <text class="info-row__label">快乐星球 · 共同话题</text>
                <view class="tag-row">
                  <view
                    v-for="(tag, idx) in focus.interests"
                    :key="tag"
                    class="tag-pill"
                    :style="tagStyle(idx + 2)"
                  >
                    <text>{{ tag }}</text>
                  </view>
                </view>
              </view>
            </view>

            <!-- 小贴士 -->
            <view class="tip-card" :class="'tip-card--' + analysisTip.tone">
              <text class="tip-card__icon">{{ analysisTip.icon }}</text>
              <text class="tip-card__text">{{ analysisTip.text }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 维护建议 -->
      <view v-else-if="activeTab === 'suggest' && focus" class="insight-body">
        <view class="focus-card">
          <view class="focus-card__hero focus-card__hero--suggest">
            <text class="focus-card__suggest-title">✨ 给 {{ focus.name }} 的维护锦囊</text>
            <view class="focus-card__score focus-card__score--light">
              <text class="focus-card__score-num">{{ focus.intimacy }}</text>
              <text class="focus-card__score-unit">%</text>
            </view>
          </view>
          <view class="focus-card__body focus-card__body--tips">
            <view
              v-for="(tip, idx) in suggestTips"
              :key="idx"
              class="suggest-tip"
              :class="'suggest-tip--' + tip.tone"
            >
              <view class="suggest-tip__badge">
                <text>{{ tip.icon }}</text>
              </view>
              <text class="suggest-tip__text">{{ tip.text }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 预警提示 -->
      <view v-else-if="activeTab === 'warning'" class="insight-body">
        <view v-if="warnings.length" class="insight-warnings">
          <view
            v-for="w in warnings"
            :key="w.contactId"
            class="insight-warning"
            :class="'insight-warning--' + w.level"
            @tap.stop="emit('focusContact', w.contactId)"
          >
            <text class="insight-warning__icon">{{ w.level === 'high' ? '🚨' : '⚠️' }}</text>
            <view class="insight-warning__content">
              <text class="insight-warning__name">{{ w.name }}</text>
              <text class="insight-warning__msg">{{ w.message }}</text>
            </view>
          </view>
        </view>
        <view v-else class="insight-empty insight-empty--good">
          <text class="insight-empty__icon">🌈</text>
          <text>暂无预警，关系网络状态良好</text>
        </view>
      </view>

      <view v-if="!focus && activeTab !== 'warning'" class="insight-empty">
        <text>暂无联系人数据</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { RelationContact } from '@/types/relation'
import type { RelationInsightTab, RelationInsightTabItem } from '@/types/relationInsight'
import {
  buildAnalysisTip,
  buildQueryText,
  buildSuggestTips,
  buildWarnings,
  focusLabels,
  resolveFocusContact,
  shouldShowQueryBar,
} from '@/utils/relationInsight'
import {
  getRelationPalette,
  insightHeroGradient,
  intimacyBarColor,
  tagPaletteByIndex,
} from '@/constants/freshTheme'
import { RELATION_TYPE_ICON } from '@/constants/relationCopy'
import { intimacyDescription } from '@/utils/relationGraph'

const props = defineProps<{
  contacts: RelationContact[]
  selectedId?: string | null
}>()

const emit = defineEmits<{
  focusContact: [id: string]
}>()

const tabs: (RelationInsightTabItem & { icon: string; color: string })[] = [
  { id: 'analysis', label: '关系分析', icon: '🔍', color: '#3B9EFF' },
  { id: 'suggest', label: '维护建议', icon: '💡', color: '#FF7EB3' },
  { id: 'warning', label: '预警提示', icon: '🔔', color: '#FFAB40' },
]

const activeTab = ref<RelationInsightTab>('analysis')

const focus = computed(() =>
  resolveFocusContact(props.contacts, props.selectedId),
)

const showQuery = computed(() => shouldShowQueryBar(focus.value))

const queryText = computed(() =>
  focus.value ? buildQueryText(focus.value) : '',
)

const suggestTips = computed(() =>
  focus.value ? buildSuggestTips(focus.value) : [],
)

const analysisTip = computed(() =>
  focus.value
    ? buildAnalysisTip(focus.value)
    : { icon: '✨', text: '', tone: 'mint' as const },
)

const warnings = computed(() => buildWarnings(props.contacts))

const displayLabels = computed(() =>
  focus.value ? focusLabels(focus.value) : [],
)

const heroGradient = computed(() =>
  focus.value ? insightHeroGradient(focus.value.relationType) : '#F7FBFF',
)

const relationMain = computed(() =>
  focus.value ? getRelationPalette(focus.value.relationType).main : '#3B9EFF',
)

const relationLight = computed(() =>
  focus.value ? getRelationPalette(focus.value.relationType).light : '#E8F4FF',
)

const relationIcon = computed(() =>
  focus.value ? RELATION_TYPE_ICON[focus.value.relationType] : '✨',
)

const intimacyColor = computed(() =>
  focus.value ? intimacyBarColor(focus.value.intimacy) : '#3B9EFF',
)

const intimacyDesc = computed(() =>
  focus.value ? intimacyDescription(focus.value.intimacy) : '',
)

function tagStyle(index: number) {
  const p = tagPaletteByIndex(index)
  return { background: p.bg, color: p.text }
}

function openAnalysis() {
  activeTab.value = 'analysis'
  if (focus.value) emit('focusContact', focus.value.id)
}
</script>

<style scoped>
.insight-section {
  margin-top: 24rpx;
  padding-bottom: 8rpx;
}

.query-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  padding: 22rpx 28rpx;
  margin-bottom: 20rpx;
  background: linear-gradient(90deg, #ffe8f2, #e8f4ff);
  border-radius: 16rpx;
  border: 1rpx solid rgba(255, 126, 179, 0.2);
}

.query-bar__left {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-width: 0;
}

.query-bar__icon {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: linear-gradient(145deg, #6bb8ff, #ff6b9d);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.query-bar__icon-text {
  font-size: 24rpx;
  font-weight: 700;
  color: #fff;
}

.query-bar__text {
  flex: 1;
  font-size: 26rpx;
  color: #2a3441;
  line-height: 1.45;
}

.query-bar__action {
  font-size: 24rpx;
  color: #1e88e5;
  flex-shrink: 0;
  font-weight: 600;
}

.insight-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx 28rpx 32rpx;
  box-shadow: 0 8rpx 32rpx rgba(59, 158, 255, 0.1);
  border: 1rpx solid rgba(255, 126, 179, 0.12);
}

.insight-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.insight-tabs {
  display: flex;
  align-items: center;
  gap: 28rpx;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.insight-tab {
  position: relative;
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding-bottom: 12rpx;
}

.insight-tab__emoji {
  font-size: 24rpx;
}

.insight-tab__label {
  font-size: 28rpx;
  color: #8e9dab;
  font-weight: 500;
}

.insight-tab--active .insight-tab__label {
  color: #2a3441;
  font-weight: 700;
}

.insight-tab__line {
  position: absolute;
  left: 50%;
  bottom: 0;
  transform: translateX(-50%);
  width: 48rpx;
  height: 6rpx;
  border-radius: 3rpx;
}

.insight-brand {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
  padding-top: 4rpx;
}

.insight-brand__dots {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

.insight-brand__dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
}

.insight-brand__dot--green { background: #2ecfa0; }
.insight-brand__dot--yellow { background: #ffb347; }
.insight-brand__dot--blue { background: #3b9eff; }

.insight-brand__name {
  font-size: 24rpx;
  color: #5c6b7a;
  font-weight: 600;
}

.insight-card__disclaimer {
  display: block;
  font-size: 22rpx;
  color: #b0bec5;
  margin-top: 16rpx;
  margin-bottom: 24rpx;
}

/* ========== 聚焦卡片 ========== */
.focus-card {
  border-radius: 20rpx;
  overflow: hidden;
  border: 1rpx solid rgba(59, 158, 255, 0.1);
  box-shadow: 0 4rpx 20rpx rgba(255, 126, 179, 0.08);
}

.focus-card__hero {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 28rpx 28rpx 24rpx;
}

.focus-card__hero--suggest {
  background: linear-gradient(135deg, #ffe8f2 0%, #e8f4ff 100%);
  justify-content: space-between;
}

.focus-card__avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.95);
  border: 4rpx solid rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.1);
}

.focus-card__avatar text {
  font-size: 36rpx;
  font-weight: 700;
}

.focus-card__hero-text {
  flex: 1;
  min-width: 0;
}

.focus-card__name {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #2a3441;
}

.focus-card__relation-pill {
  display: inline-block;
  margin-top: 10rpx;
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  border: 1rpx solid transparent;
}

.focus-card__relation-pill text {
  font-size: 22rpx;
  font-weight: 600;
}

.focus-card__score {
  text-align: center;
  padding: 12rpx 20rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.92);
  flex-shrink: 0;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.08);
}

.focus-card__score--light {
  background: #fff;
}

.focus-card__score-num {
  font-size: 36rpx;
  font-weight: 800;
  color: #ff6b9d;
}

.focus-card__score-unit {
  font-size: 22rpx;
  font-weight: 600;
  color: #3b9eff;
}

.focus-card__suggest-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #2a3441;
}

.focus-card__body {
  padding: 24rpx;
  background: linear-gradient(180deg, #fffbfe 0%, #f8fbff 100%);
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.focus-card__body--tips {
  gap: 14rpx;
}

/* 信息行 */
.info-row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 20rpx 22rpx;
  border-radius: 16rpx;
  border: 1rpx solid transparent;
}

.info-row--pink {
  background: linear-gradient(135deg, #fff0f6, #ffe8f2);
  border-color: rgba(255, 107, 157, 0.15);
}

.info-row--blue {
  background: linear-gradient(135deg, #f0f8ff, #e8f4ff);
  border-color: rgba(59, 158, 255, 0.15);
}

.info-row--purple {
  background: linear-gradient(135deg, #f8f4ff, #f3eeff);
  border-color: rgba(167, 139, 250, 0.15);
}

.info-row--mint {
  background: linear-gradient(135deg, #f0fdf8, #e6fbf3);
  border-color: rgba(46, 207, 160, 0.15);
}

.info-row__icon {
  font-size: 32rpx;
  line-height: 1.2;
  flex-shrink: 0;
}

.info-row__main {
  flex: 1;
  min-width: 0;
}

.info-row__label {
  display: block;
  font-size: 24rpx;
  font-weight: 600;
  color: #5c6b7a;
  margin-bottom: 10rpx;
}

.info-row__value {
  font-size: 28rpx;
  color: #2a3441;
  line-height: 1.55;
}

.info-row__desc {
  display: block;
  font-size: 24rpx;
  color: #ff7eb3;
  margin-top: 8rpx;
  font-weight: 500;
}

.intimacy-track {
  height: 14rpx;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 8rpx;
  overflow: hidden;
  border: 1rpx solid rgba(255, 126, 179, 0.15);
}

.intimacy-track__fill {
  height: 100%;
  border-radius: 8rpx;
  transition: width 0.4s ease;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.tag-pill {
  padding: 8rpx 20rpx;
  border-radius: 24rpx;
}

.tag-pill text {
  font-size: 24rpx;
  font-weight: 600;
}

/* 小贴士 */
.tip-card {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  padding: 22rpx 24rpx;
  border-radius: 16rpx;
  margin-top: 4rpx;
}

.tip-card--warm {
  background: linear-gradient(135deg, #fff8e8, #ffe8cc);
  border: 1rpx solid rgba(255, 171, 64, 0.25);
}

.tip-card--mint {
  background: linear-gradient(135deg, #e8fbf3, #d4f5e8);
  border: 1rpx solid rgba(46, 207, 160, 0.25);
}

.tip-card__icon {
  font-size: 32rpx;
  flex-shrink: 0;
}

.tip-card__text {
  flex: 1;
  font-size: 26rpx;
  color: #2a3441;
  line-height: 1.55;
  font-weight: 500;
}

/* 维护建议 */
.suggest-tip {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 22rpx 24rpx;
  border-radius: 16rpx;
  border: 1rpx solid transparent;
}

.suggest-tip--blue {
  background: linear-gradient(135deg, #f0f8ff, #e8f4ff);
  border-color: rgba(59, 158, 255, 0.18);
}

.suggest-tip--purple {
  background: linear-gradient(135deg, #f8f4ff, #f3eeff);
  border-color: rgba(167, 139, 250, 0.18);
}

.suggest-tip--warm {
  background: linear-gradient(135deg, #fff8e8, #ffe8cc);
  border-color: rgba(255, 171, 64, 0.22);
}

.suggest-tip--mint {
  background: linear-gradient(135deg, #f0fdf8, #e6fbf3);
  border-color: rgba(46, 207, 160, 0.22);
}

.suggest-tip__badge {
  width: 52rpx;
  height: 52rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.85);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 28rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.06);
}

.suggest-tip__text {
  flex: 1;
  font-size: 28rpx;
  color: #2a3441;
  line-height: 1.6;
}

/* 预警 */
.insight-warnings {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.insight-warning {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 24rpx 28rpx;
  border-radius: 16rpx;
  border: 1rpx solid transparent;
}

.insight-warning--medium {
  background: linear-gradient(135deg, #fff8e8, #fff3e0);
  border-color: rgba(255, 171, 64, 0.25);
  border-left: 6rpx solid #ffb347;
}

.insight-warning--high {
  background: linear-gradient(135deg, #fff0f0, #ffe8e8);
  border-color: rgba(255, 107, 107, 0.25);
  border-left: 6rpx solid #ff6b6b;
}

.insight-warning__icon {
  font-size: 32rpx;
}

.insight-warning__name {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #2a3441;
  margin-bottom: 6rpx;
}

.insight-warning__msg {
  font-size: 26rpx;
  color: #5c6b7a;
  line-height: 1.5;
}

.insight-empty {
  padding: 48rpx 24rpx;
  text-align: center;
}

.insight-empty--good {
  background: linear-gradient(135deg, #f0fdf8, #e8f4ff);
  border-radius: 16rpx;
}

.insight-empty__icon {
  display: block;
  font-size: 48rpx;
  margin-bottom: 12rpx;
}

.insight-empty text {
  font-size: 26rpx;
  color: #5c6b7a;
}

@media (min-width: 768px) {
  .insight-section { margin-top: 20px; }

  .query-bar {
    padding: 14px 20px;
    margin-bottom: 16px;
    border-radius: 12px;
  }

  .query-bar__icon { width: 32px; height: 32px; }
  .query-bar__icon-text { font-size: 14px; }
  .query-bar__text { font-size: 14px; }
  .query-bar__action { font-size: 13px; }

  .insight-card {
    border-radius: 14px;
    padding: 20px 24px 24px;
  }

  .insight-tab__label { font-size: 15px; }
  .insight-tab__line { width: 32px; height: 3px; }

  .focus-card__hero { padding: 18px 20px 16px; }
  .focus-card__avatar { width: 48px; height: 48px; }
  .focus-card__avatar text { font-size: 20px; }
  .focus-card__name { font-size: 17px; }
  .focus-card__body { padding: 16px; gap: 12px; }

  .info-row { padding: 14px 16px; }
  .info-row__label { font-size: 13px; }
  .info-row__value,
  .suggest-tip__text { font-size: 14px; }

  .tag-pill text { font-size: 13px; }
}
</style>

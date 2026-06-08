<template>
  <view class="relation-page" :class="{ 'relation-page--wide': isWide }" :style="pageBgStyle">
    <!-- 标语区 -->
    <view class="page-banner">
      <view class="page-banner__icon-wrap">
        <text class="page-banner__icon">{{ copy.banner.icon }}</text>
      </view>
      <view class="page-banner__text">
        <text class="page-banner__title">{{ copy.banner.title }}</text>
        <text class="page-banner__subtitle">{{ copy.banner.subtitle }}</text>
      </view>
    </view>

    <view class="page-actions">
      <view class="page-actions__stats">
        <view class="stat-chip stat-chip--blue">
          <view class="stat-chip__icon-box stat-chip__icon-box--blue">
            <text>{{ copy.stats.contacts.icon }}</text>
          </view>
          <view class="stat-chip__content">
            <text class="stat-chip__num">{{ contacts.length }}</text>
            <text class="stat-chip__label">{{ copy.stats.contacts.label }}</text>
          </view>
        </view>
        <view class="stat-chip stat-chip--warm">
          <view class="stat-chip__icon-box stat-chip__icon-box--warm">
            <text>{{ copy.stats.intimacy.icon }}</text>
          </view>
          <view class="stat-chip__content">
            <text class="stat-chip__num">{{ avgIntimacy }}</text>
            <text class="stat-chip__label">{{ copy.stats.intimacy.label }}</text>
          </view>
        </view>
      </view>

      <view class="view-switch">
        <view
          class="view-switch__item"
          :class="{ 'view-switch__item--active': viewMode === 'graph' }"
          @tap.stop="setViewMode('graph')"
        >
          <view class="view-switch__icon-box">
            <text class="view-switch__emoji">{{ copy.viewModes.graph.icon }}</text>
          </view>
          <text class="view-switch__label">{{ copy.viewModes.graph.label }}</text>
        </view>
        <view
          class="view-switch__item"
          :class="{ 'view-switch__item--active': viewMode === 'list' }"
          @tap.stop="setViewMode('list')"
        >
          <view class="view-switch__icon-box">
            <text class="view-switch__emoji">{{ copy.viewModes.list.icon }}</text>
          </view>
          <text class="view-switch__label">{{ copy.viewModes.list.label }}</text>
        </view>
      </view>
    </view>

    <view class="page-body">
      <view
        class="main-card"
        :class="{
          'main-card--graph': viewMode === 'graph',
          'main-card--list': viewMode === 'list',
        }"
      >
        <view class="main-card__primary">
          <view v-if="viewMode === 'graph'" class="legend">
            <view class="legend__row">
              <text class="legend__title">{{ copy.legend.relationTitle }}</text>
              <view class="legend__types">
                <view
                  v-for="item in relationLegend"
                  :key="item.type"
                  class="legend__type-item"
                >
                  <text class="legend__emoji">{{ item.icon }}</text>
                  <view
                    class="legend__dot"
                    :style="{ background: getRelationPalette(item.type).main }"
                  />
                  <text class="legend__text">{{ item.label }}</text>
                </view>
              </view>
            </view>
            <view class="legend__row legend__row--intimacy">
              <text class="legend__title">{{ copy.legend.intimacyTitle }}</text>
              <view class="legend__scale">
                <view
                  v-for="item in intimacyLegend"
                  :key="item.label"
                  class="legend__item"
                >
                  <view
                    class="legend__line"
                    :style="{ background: item.color, height: item.height + 'px' }"
                  />
                  <text class="legend__text">{{ intimacyFunLabel(item.label) }}</text>
                </view>
              </view>
            </view>
            <text class="legend__hint">{{ copy.legend.hint }}</text>
          </view>

          <!-- 窄屏图谱：快速寻星搜索 -->
          <RelationStarSearch
            v-if="viewMode === 'graph' && !isWide"
            class="graph-search"
            :contacts="contacts"
            :keyword="searchKeyword"
            @update:keyword="searchKeyword = $event"
            @select="onSearchSelect"
          />

          <view v-if="viewMode === 'graph'" class="graph-panel">
            <RelationGraphChart
              ref="chartRef"
              :contacts="contacts"
              :selected-id="chartHighlightId"
              fill-parent
              @select="onSelectFromGraph"
            />
          </view>

          <view v-else class="list-panel">
            <view class="list-header">
              <view class="list-header__icon-wrap">
                <text>{{ copy.listHeader.icon }}</text>
              </view>
              <view class="list-header__text">
                <text class="list-header__title">{{ copy.listHeader.title }}</text>
                <text class="list-header__hint">{{ copy.listHeader.hint }}</text>
              </view>
            </view>
            <RelationContactList
              :contacts="contacts"
              :selected-id="chartHighlightId"
              :grid="isWide"
              @select="onSelectFromList"
            />
          </view>
        </view>

        <!-- 宽屏 + 图谱模式：右侧常驻通讯录 -->
        <view v-if="showSidebar" class="main-card__sidebar">
          <RelationStarSearch
            compact
            :contacts="contacts"
            :keyword="searchKeyword"
            @update:keyword="searchKeyword = $event"
            @select="onSearchSelect"
          />
          <RelationContactList
            :contacts="contacts"
            :selected-id="chartHighlightId"
            :filter-keyword="searchKeyword"
            compact
            @select="onSelectFromList"
          />
        </view>
      </view>

      <!-- 关系分析 / 维护建议 / 预警（参考产品稿） -->
      <RelationInsightPanel
        :contacts="contacts"
        :selected-id="insightFocusId"
        @focus-contact="onInsightFocus"
      />
    </view>

    <!-- 人物详情弹层（点击图谱节点 / 通讯录） -->
    <RelationPersonDrawer
      :show="drawerVisible"
      :contact="selectedContact"
      @close="closePersonDrawer"
    />

    <CustomTabBar />
    <FloatingActionButton />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import CustomTabBar from '@/components/CustomTabBar.vue'
import FloatingActionButton from '@/components/FloatingActionButton.vue'
import RelationGraphChart from '@/components/relation/RelationGraphChart.vue'
import RelationContactList from '@/components/relation/RelationContactList.vue'
import RelationPersonDrawer from '@/components/relation/RelationPersonDrawer.vue'
import RelationInsightPanel from '@/components/relation/RelationInsightPanel.vue'
import RelationStarSearch from '@/components/relation/RelationStarSearch.vue'
import { MOCK_RELATION_CONTACTS } from '@/data/mockRelations'
import type { RelationContact, RelationViewMode } from '@/types/relation'
import {
  INTIMACY_LEGEND,
  RELATION_LEGEND,
  WEB,
  getRelationPalette,
} from '@/constants/freshTheme'
import {
  RELATION_PAGE,
  INTIMACY_LEGEND_FUN,
} from '@/constants/relationCopy'

const copy = RELATION_PAGE

const pageBgStyle = { background: WEB.pageBgGradient }

function intimacyFunLabel(label: string): string {
  return INTIMACY_LEGEND_FUN[label] ?? label
}

const WIDE_BREAKPOINT = 768

const contacts = ref<RelationContact[]>([...MOCK_RELATION_CONTACTS])
const viewMode = ref<RelationViewMode>('graph')
/** 图谱 / 列表高亮 */
const insightFocusId = ref<string | null>(null)
/** 详情抽屉（仅点击节点 / 列表项打开） */
const drawerOpenId = ref<string | null>(null)
const searchKeyword = ref('')
const chartRef = ref<InstanceType<typeof RelationGraphChart> | null>(null)
const isWide = ref(false)

const chartHighlightId = computed(
  () => drawerOpenId.value ?? insightFocusId.value,
)

const intimacyLegend = INTIMACY_LEGEND
const relationLegend = RELATION_LEGEND

const avgIntimacy = computed(() => {
  if (!contacts.value.length) return 0
  const sum = contacts.value.reduce((a, c) => a + c.intimacy, 0)
  return Math.round(sum / contacts.value.length)
})

const selectedContact = computed(() =>
  contacts.value.find((c) => c.id === drawerOpenId.value) ?? null,
)

const drawerVisible = computed(() => selectedContact.value !== null)

/** 宽屏且为图谱模式时显示右侧栏 */
const showSidebar = computed(() => isWide.value && viewMode.value === 'graph')

function setViewMode(mode: RelationViewMode) {
  if (viewMode.value === mode) return
  viewMode.value = mode
  if (mode === 'graph') {
    nextTick(() => {
      setTimeout(() => chartRef.value?.resize(), 150)
    })
  }
}

function syncWide() {
  try {
    isWide.value = uni.getSystemInfoSync().windowWidth >= WIDE_BREAKPOINT
  } catch {
    isWide.value = false
  }
}

function onSelectFromGraph(id: string | null) {
  if (!id) {
    drawerOpenId.value = null
    return
  }
  drawerOpenId.value = id
  insightFocusId.value = id
}

function onSelectFromList(id: string) {
  drawerOpenId.value = id
  insightFocusId.value = id
}

function closePersonDrawer() {
  drawerOpenId.value = null
}

function onInsightFocus(id: string) {
  insightFocusId.value = id
}

function onSearchSelect(id: string) {
  const contact = contacts.value.find((c) => c.id === id)
  if (contact) {
    searchKeyword.value = contact.name
  }
  insightFocusId.value = id
}

watch(isWide, async () => {
  await nextTick()
  setTimeout(() => chartRef.value?.resize(), 150)
})

let resizeTimer: ReturnType<typeof setTimeout> | null = null
function onWindowResize() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = setTimeout(() => {
    syncWide()
    chartRef.value?.resize()
  }, 120)
}

onMounted(() => {
  syncWide()
  uni.onWindowResize(onWindowResize)
})

onUnmounted(() => {
  uni.offWindowResize(onWindowResize)
  if (resizeTimer) clearTimeout(resizeTimer)
})

onShow(() => {
  syncWide()
  if (viewMode.value === 'graph') {
    setTimeout(() => chartRef.value?.resize(), 150)
  }
})
</script>

<style scoped>
/* ========== 页面根 ========== */
.relation-page {
  width: 100%;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  padding-bottom: calc(140rpx + env(safe-area-inset-bottom));
  overflow-y: auto;
}

.relation-page--wide {
  padding-bottom: 72px;
  min-height: 100vh;
  height: auto;
  overflow: auto;
}

/* ========== 标语横幅 ========== */
.page-banner {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin: 16rpx 24rpx 0;
  padding: 24rpx 28rpx;
  background: linear-gradient(135deg, #ffffff 0%, #ffe8f4 40%, #e8f4ff 100%);
  border-radius: 20rpx;
  border: 1rpx solid rgba(255, 126, 179, 0.2);
  box-shadow: 0 6rpx 24rpx rgba(255, 126, 179, 0.12);
}

.page-banner__icon-wrap {
  width: 88rpx;
  height: 88rpx;
  border-radius: 24rpx;
  background: linear-gradient(145deg, #ffb8d9, #8cc8ff);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4rpx 16rpx rgba(255, 126, 179, 0.25);
}

.page-banner__icon {
  font-size: 44rpx;
  line-height: 1;
}

.page-banner__text {
  flex: 1;
  min-width: 0;
}

.page-banner__title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: #2a3441;
  letter-spacing: 1rpx;
}

.page-banner__subtitle {
  display: block;
  font-size: 24rpx;
  color: #6b7c8d;
  margin-top: 8rpx;
  line-height: 1.45;
}

/* ========== 工具区 ========== */
.page-actions {
  flex-shrink: 0;
  padding: 16rpx 24rpx;
  box-sizing: border-box;
}

.page-actions__stats {
  display: flex;
  gap: 16rpx;
}

.stat-chip {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 16rpx;
  border-radius: 16rpx;
  padding: 18rpx 20rpx;
  border: 1rpx solid transparent;
}

.stat-chip--blue {
  background: linear-gradient(135deg, #ffffff, #e8f4ff);
  border-color: rgba(59, 158, 255, 0.25);
  box-shadow: 0 4rpx 16rpx rgba(59, 158, 255, 0.1);
}

.stat-chip--warm {
  background: linear-gradient(135deg, #ffffff, #ffe8f2);
  border-color: rgba(255, 107, 157, 0.25);
  box-shadow: 0 4rpx 16rpx rgba(255, 107, 157, 0.1);
}

.stat-chip__icon-box {
  width: 64rpx;
  height: 64rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  flex-shrink: 0;
}

.stat-chip__icon-box--blue {
  background: linear-gradient(145deg, #8cc8ff, #3b9eff);
  box-shadow: 0 4rpx 12rpx rgba(59, 158, 255, 0.35);
}

.stat-chip__icon-box--warm {
  background: linear-gradient(145deg, #ffb8d9, #ff6b9d);
  box-shadow: 0 4rpx 12rpx rgba(255, 107, 157, 0.35);
}

.stat-chip__content {
  flex: 1;
  min-width: 0;
}

.stat-chip--blue .stat-chip__num {
  color: #1e88e5;
}

.stat-chip--warm .stat-chip__num {
  color: #e91e8c;
}

.stat-chip__num {
  display: block;
  font-size: 36rpx;
  font-weight: 700;
}

.stat-chip__label {
  display: block;
  font-size: 22rpx;
  color: #6b7c8d;
  margin-top: 4rpx;
}

.view-switch {
  display: flex;
  margin-top: 20rpx;
  padding: 6rpx;
  background: linear-gradient(90deg, #ffe8f2, #e8f4ff);
  border-radius: 48rpx;
  border: 1rpx solid rgba(255, 126, 179, 0.15);
}

.view-switch__item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  padding: 18rpx 0;
  border-radius: 40rpx;
  transition: all 0.22s ease;
}

.view-switch__item--active {
  background: #ffffff;
  box-shadow: 0 4rpx 16rpx rgba(59, 158, 255, 0.18);
}

.view-switch__icon-box {
  width: 48rpx;
  height: 48rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
}

.view-switch__item--active .view-switch__icon-box {
  background: linear-gradient(145deg, #e8f4ff, #ffe8f2);
}

.view-switch__emoji {
  font-size: 26rpx;
  line-height: 1;
}

.view-switch__item--active .view-switch__label {
  color: #1e88e5;
  font-weight: 600;
}

.view-switch__label {
  font-size: 28rpx;
  color: #6b7c8d;
  font-weight: 500;
}

/* ========== 主体容器 ========== */
.page-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 0 24rpx 24rpx;
  box-sizing: border-box;
}

.main-card {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 55vh;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 32rpx rgba(59, 158, 255, 0.1);
  border: 1rpx solid rgba(255, 126, 179, 0.15);
  overflow: hidden;
}

.main-card__primary {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}

.legend {
  flex-shrink: 0;
  padding: 20rpx 28rpx 12rpx;
  border-bottom: 1rpx solid rgba(255, 126, 179, 0.12);
  background: linear-gradient(90deg, #fff8fc 0%, #f8fbff 50%, #f5fff9 100%);
}

.legend__title {
  font-size: 24rpx;
  font-weight: 600;
  color: #5c6b7a;
  display: block;
  margin-bottom: 12rpx;
}

.legend__types {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx 28rpx;
}

.legend__type-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 16rpx;
  background: #fff;
  border-radius: 24rpx;
  border: 1rpx solid rgba(59, 158, 255, 0.12);
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.04);
}

.legend__emoji {
  font-size: 22rpx;
  line-height: 1;
}

.legend__dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.12);
}

.legend__scale {
  display: flex;
  align-items: flex-end;
  gap: 24rpx;
  flex-wrap: wrap;
}

.legend__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.legend__line {
  width: 48rpx;
  border-radius: 4rpx;
}

.legend__text {
  font-size: 20rpx;
  color: #5c6b7a;
  font-weight: 500;
}

.legend__hint {
  display: block;
  font-size: 20rpx;
  color: #ff7eb3;
  margin-top: 12rpx;
  font-weight: 500;
}

.legend__row {
  margin-bottom: 16rpx;
}

.legend__row--intimacy {
  margin-bottom: 8rpx;
}

.graph-search {
  flex-shrink: 0;
  border-bottom: 1rpx solid rgba(255, 126, 179, 0.1);
  background: linear-gradient(90deg, #fff8fc, #f8fbff);
}

.graph-panel {
  flex: 1;
  min-height: 360px;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: radial-gradient(ellipse 80% 60% at 50% 45%, #fff5fa 0%, #f0f7ff 70%, #ffffff 100%);
}

.list-panel {
  flex: 1;
  min-height: 360px;
  min-width: 0;
  padding: 16rpx 20rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.list-header {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 8rpx 4rpx 20rpx;
  flex-shrink: 0;
}

.list-header__icon-wrap {
  width: 72rpx;
  height: 72rpx;
  border-radius: 20rpx;
  background: linear-gradient(145deg, #ffe8f2, #e8f4ff);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  box-shadow: 0 4rpx 12rpx rgba(255, 126, 179, 0.2);
}

.list-header__text {
  flex: 1;
}

.list-header__title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #2a3441;
}

.list-header__hint {
  display: block;
  font-size: 22rpx;
  color: #6b7c8d;
  margin-top: 6rpx;
}

.list-panel .contact-list {
  flex: 1;
  min-height: 0;
}

/* 侧栏（仅宽屏图谱模式） */
.main-card__sidebar {
  display: none;
}

.sidebar-list-head {
  display: none;
}

/* ==================== 宽屏：网页式全宽布局 ==================== */
@media (min-width: 768px) {
  .relation-page--wide {
    display: flex;
    flex-direction: column;
  }

  .page-banner {
    margin: 12px 32px 0;
    padding: 16px 20px;
  }

  .page-banner__icon-wrap {
    width: 52px;
    height: 52px;
    border-radius: 14px;
  }

  .page-banner__icon {
    font-size: 28px;
  }

  .page-banner__title {
    font-size: 17px;
  }

  .page-banner__subtitle {
    font-size: 13px;
  }

  .page-actions {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 16px 24px;
    padding: 12px 32px;
  }

  .page-actions__stats {
    flex: 0 0 auto;
    gap: 16px;
  }

  .stat-chip {
    flex: none;
    min-width: 120px;
    padding: 12px 20px;
  }

  .stat-chip__num {
    font-size: 22px;
  }

  .stat-chip__label {
    font-size: 12px;
  }

  .view-switch {
    flex: 1 1 280px;
    margin-top: 0;
    max-width: 360px;
  }

  .view-switch__item {
    padding: 10px 20px;
  }

  .view-switch__label {
    font-size: 14px;
  }

  .view-switch__emoji {
    font-size: 16px;
  }

  .stat-chip__icon-box {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }

  .page-body {
    flex: 1;
    min-height: 0;
    padding: 20px 32px 24px;
    width: 100%;
    max-width: none;
  }

  .main-card {
    flex: 1;
    min-height: 0;
    border-radius: 16px;
    flex-direction: row;
  }

  .main-card--list {
    flex-direction: column;
  }

  .main-card--list .main-card__primary {
    flex: 1;
    min-height: 0;
    border-right: none;
  }

  .main-card--list .list-panel {
    flex: 1;
    min-height: 0;
    height: auto;
  }

  .main-card--graph .main-card__primary {
    flex: 1;
    border-right: 1rpx solid rgba(255, 126, 179, 0.12);
  }

  .main-card__sidebar {
    display: flex;
    flex-direction: column;
    width: 360px;
    flex-shrink: 0;
    min-height: 0;
    background: linear-gradient(180deg, #fff8fc 0%, #f8fbff 100%);
  }

  .main-card__sidebar :deep(.contact-list) {
    flex: 1;
    min-height: 0;
  }

  .main-card__sidebar :deep(.contact-list--compact) {
    padding: 0 8px 8px;
  }

  .legend {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    gap: 12px 32px;
    padding: 14px 24px;
  }

  .legend__row {
    margin-bottom: 0;
    flex: 1 1 200px;
  }

  .legend__row--intimacy {
    flex: 1 1 280px;
  }

  .legend__title {
    margin-bottom: 8px;
  }

  .legend__hint {
    width: 100%;
    margin-top: 4px;
    font-size: 12px;
  }

  .graph-panel {
    min-height: 0;
    height: 100%;
  }

  .list-panel {
    max-height: none;
    height: 100%;
    padding: 20px 28px 28px;
  }
}

@media (min-width: 1200px) {
  .page-banner {
    margin: 12px 48px 0;
  }

  .page-actions {
    padding: 12px 48px;
  }

  .page-body {
    padding: 24px 48px 28px;
  }

  .main-card__sidebar {
    width: 400px;
  }
}
</style>

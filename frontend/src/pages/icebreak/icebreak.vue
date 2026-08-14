<template>
  <view class="page-container">
    <scroll-view class="form-scroll" scroll-y>
      <view class="form-card">
        <!-- ① 当前情境 -->
        <view class="form-section">
          <text class="form-label">当前情境</text>
          <picker mode="selector" :range="CONTEXT_PRESETS" @change="onContextPick">
            <view class="picker-box">
              <text :class="context ? 'picker-value' : 'picker-placeholder'">
                {{ context || '选择场景' }}
              </text>
              <text class="picker-arrow">▾</text>
            </view>
          </picker>
          <input
            class="form-input form-input--custom"
            v-model="customContext"
            placeholder="自定义情境（如：社团迎新、老同学聚餐）"
            placeholder-style="color:#c0c4cc"
            maxlength="40"
          />
        </view>

        <!-- ② 对方名片 -->
        <view class="form-section">
          <text class="form-label">对方名片</text>
          <!-- 三个选项 -->
          <view class="mode-row">
            <view
              class="mode-chip"
              :class="{ 'mode-chip--active': otherMode === 'none' }"
              @tap="otherMode = 'none'"
            >
              <text class="mode-chip-text">无</text>
            </view>
            <view
              class="mode-chip"
              :class="{ 'mode-chip--active': otherMode === 'select' }"
              @tap="otherMode = 'select'"
            >
              <text class="mode-chip-text">下拉选择</text>
            </view>
            <view
              class="mode-chip"
              :class="{ 'mode-chip--active': otherMode === 'scan' }"
              @tap="handleScan"
            >
              <text class="mode-chip-text">扫码</text>
            </view>
          </view>

          <!-- 下拉选择：已有联系人 / 虚拟人物 -->
          <view v-if="otherMode === 'select'" class="select-panel">
            <view class="sub-mode-row">
              <view
                class="sub-mode-chip"
                :class="{ 'sub-mode-chip--active': selectSource === 'contact' }"
                @tap="selectSource = 'contact'"
              >
                <text class="sub-mode-chip-text">已有联系人</text>
              </view>
              <view
                class="sub-mode-chip"
                :class="{ 'sub-mode-chip--active': selectSource === 'virtual' }"
                @tap="selectSource = 'virtual'"
              >
                <text class="sub-mode-chip-text">虚拟人物</text>
              </view>
            </view>

            <picker
              v-if="selectSource === 'contact'"
              mode="selector"
              :range="contactNames"
              @change="onContactPick"
            >
              <view class="picker-box picker-box--small">
                <text :class="contactIndex >= 0 ? 'picker-value' : 'picker-placeholder'">
                  {{ contactIndex >= 0 ? contactNames[contactIndex] : '选择已有联系人' }}
                </text>
                <text class="picker-arrow">▾</text>
              </view>
            </picker>

            <picker
              v-if="selectSource === 'virtual'"
              mode="selector"
              :range="virtualNames"
              @change="onVirtualPick"
            >
              <view class="picker-box picker-box--small">
                <text :class="virtualIndex >= 0 ? 'picker-value' : 'picker-placeholder'">
                  {{ virtualIndex >= 0 ? virtualNames[virtualIndex] : '选择虚拟人物' }}
                </text>
                <text class="picker-arrow">▾</text>
              </view>
            </picker>

            <view v-if="selectSource === 'virtual'" class="manage-link" @tap="goManageVirtual">
              <text class="manage-link-text">＋ 管理虚拟人物</text>
            </view>
          </view>

          <!-- 扫码结果 -->
          <view v-if="otherMode === 'scan'" class="scan-result">
            <text v-if="scanText" class="scan-result-text">{{ scanText }}</text>
            <text v-else class="scan-result-text scan-result-text--empty">点击上方「扫码」开始</text>
          </view>
        </view>

        <!-- ③ 我的状态 -->
        <view class="form-section">
          <text class="form-label">我的状态</text>
          <view class="mood-tags">
            <view
              v-for="m in MOOD_TAGS"
              :key="m.label"
              class="mood-tag"
              :class="{ 'mood-tag--active': selectedMood.includes(m.label) }"
              :style="selectedMood.includes(m.label) ? moodActiveStyle(m) : {}"
              @tap="toggleMood(m.label)"
            >
              <text class="mood-tag-text">{{ m.label }}</text>
            </view>
          </view>
          <!-- 自定义状态 -->
          <view class="custom-mood">
            <view class="custom-mood-toggle" @tap="customMoodVisible = !customMoodVisible">
              <text class="custom-mood-toggle-text">＋ 自定义状态</text>
            </view>
            <view v-if="customMoodVisible" class="custom-mood-input-row">
              <input
                class="form-input custom-mood-input"
                v-model="customMoodInput"
                placeholder="输入心情/状态，如：期待"
                placeholder-style="color:#c0c4cc"
                maxlength="12"
              />
              <view class="custom-mood-add" @tap="addCustomMood">
                <text class="custom-mood-add-text">添加</text>
              </view>
            </view>
            <!-- 已选自定义状态 -->
            <view v-if="customMoods.length" class="mood-tags mood-tags--custom">
              <view
                v-for="m in customMoods"
                :key="m"
                class="mood-tag mood-tag--custom-item"
                :class="{ 'mood-tag--active': selectedMood.includes(m) }"
                :style="selectedMood.includes(m) ? { backgroundColor: '#8B5CF6', borderColor: '#8B5CF6' } : {}"
                @tap="toggleMood(m)"
              >
                <text class="mood-tag-text">{{ m }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- ④ 额外补充 -->
        <view class="form-section">
          <text class="form-label">额外补充</text>
          <textarea
            class="form-textarea"
            v-model="extraNote"
            placeholder="补充一些背景信息，让建议更贴合（如：对方是我新同事，第一次见面）"
            placeholder-style="color:#c0c4cc"
            :maxlength="200"
            auto-height
          />
        </view>
      </view>

      <!-- ⑤ 生成妙计按钮 -->
      <view class="submit-wrap">
        <view
          class="submit-btn"
          :class="{ 'submit-btn--loading': submitting }"
          @tap="handleSubmit"
        >
          <text class="submit-btn-text">{{ submitting ? '正在生成…' : '点击生成妙计' }}</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { icebreakAnalysis, getVirtualCharacters } from '@/api/simulation'
import { getRelationGraph } from '@/api/relation'
import { getUserProfile } from '@/api/user'
import { icebreakResultStore } from '@/store/icebreak'
import type { GraphContact } from '@/types/relationGraph'
import type { VirtualCharacter } from '@/types/simulation'

// ── ① 当前情境 ──
const CONTEXT_PRESETS = ['初次见面', '读书会', '工作会议', '聚会', '面试', '社团活动']
const context = ref('')
const customContext = ref('')

function onContextPick(e: any) {
  const idx = Number(e.detail.value)
  context.value = CONTEXT_PRESETS[idx]
}

/** 最终情境：自定义优先，否则取下拉预设 */
const finalContext = computed(() => customContext.value.trim() || context.value || '初次见面')

// ── ② 对方名片 ──
const otherMode = ref<'none' | 'select' | 'scan'>('none')
const selectSource = ref<'contact' | 'virtual'>('contact')

const contacts = ref<GraphContact[]>([])
const virtuals = ref<VirtualCharacter[]>([])
const contactIndex = ref(-1)
const virtualIndex = ref(-1)
const scanText = ref('')

const contactNames = computed(() => contacts.value.map((c) => c.name))
const virtualNames = computed(() => virtuals.value.map((v) => v.name))

function onContactPick(e: any) {
  contactIndex.value = Number(e.detail.value)
  virtualIndex.value = -1
}

function onVirtualPick(e: any) {
  virtualIndex.value = Number(e.detail.value)
  contactIndex.value = -1
}

/** 对方名片数据 */
const otherCard = computed(() => {
  if (otherMode.value === 'select') {
    if (selectSource.value === 'virtual' && virtualIndex.value >= 0) {
      const v = virtuals.value[virtualIndex.value]
      if (v) {
        return { interests: v.interests || [], labels: v.labels || [], personality: v.personality || '' }
      }
    }
    if (selectSource.value === 'contact' && contactIndex.value >= 0) {
      const c = contacts.value[contactIndex.value]
      if (c) {
        return { interests: c.interests || [], labels: c.labels || [], personality: c.personality || '' }
      }
    }
    return { interests: [], labels: [], personality: '' }
  }
  if (otherMode.value === 'scan') {
    // 扫码内容：尝试按 JSON 名片解析，否则当作姓名
    return parseScan(scanText.value)
  }
  return { interests: [], labels: [], personality: '' }
})

function parseScan(text: string) {
  if (!text) return { interests: [], labels: [], personality: '' }
  try {
    const obj = JSON.parse(text)
    return {
      interests: Array.isArray(obj.interests) ? obj.interests : [],
      labels: Array.isArray(obj.labels) ? obj.labels : [],
      personality: typeof obj.personality === 'string' ? obj.personality : '',
    }
  } catch {
    // 非 JSON，按姓名处理（无名片详情）
    return { interests: [], labels: [], personality: '' }
  }
}

function handleScan() {
  otherMode.value = 'scan'
  uni.scanCode({
    success: (res) => {
      scanText.value = res.result || ''
    },
    fail: () => {
      uni.showToast({ title: '扫码失败', icon: 'none' })
    },
  })
}

function goManageVirtual() {
  uni.navigateTo({ url: '/pages/simulation/virtual-characters' })
}

// ── ③ 我的状态 ──
const MOOD_TAGS = [
  { label: '紧张', color: '#EF4444' },
  { label: '兴奋', color: '#F59E0B' },
  { label: '疲惫', color: '#6B7280' },
  { label: '放松', color: '#10B981' },
  { label: '社恐', color: '#6366F1' },
  { label: '自信', color: '#3B82F6' },
  { label: '好奇', color: '#8B5CF6' },
  { label: '平静', color: '#14B8A6' },
]
const selectedMood = ref<string[]>([])
const customMoods = ref<string[]>([])
const customMoodVisible = ref(false)
const customMoodInput = ref('')

function moodActiveStyle(m: { label: string; color: string }) {
  return { backgroundColor: m.color, borderColor: m.color }
}

function toggleMood(label: string) {
  const idx = selectedMood.value.indexOf(label)
  if (idx >= 0) {
    selectedMood.value.splice(idx, 1)
  } else {
    selectedMood.value.push(label)
  }
}

function addCustomMood() {
  const t = customMoodInput.value.trim()
  if (!t) {
    uni.showToast({ title: '请输入状态', icon: 'none' })
    return
  }
  if (!customMoods.value.includes(t)) {
    customMoods.value.push(t)
    selectedMood.value.push(t)
  }
  customMoodInput.value = ''
  customMoodVisible.value = false
}

// ── ④ 额外补充 ──
const extraNote = ref('')

// ── 我的名片（从用户画像 Agent 加载，多智能体数据集成）──
const myCard = reactive({ interests: [] as string[], labels: [] as string[] })

// ── 提交 ──
const submitting = ref(false)

async function handleSubmit() {
  if (submitting.value) return
  submitting.value = true
  try {
    const res = await icebreakAnalysis({
      myInterests: myCard.interests,
      myLabels: myCard.labels,
      myMood: selectedMood.value,
      otherInterests: otherCard.value.interests,
      otherLabels: otherCard.value.labels,
      otherPersonality: otherCard.value.personality,
      context: [finalContext.value, extraNote.value.trim()].filter(Boolean).join('；') || '初次见面',
    })

    if (res.code === 0 && res.data) {
      icebreakResultStore.analysis = res.data.analysis
      uni.navigateTo({ url: '/pages/icebreak/icebreak-result' })
    } else {
      uni.showToast({ title: '生成失败，请重试', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '网络异常，请重试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}

// ── 加载数据 ──
async function loadData() {
  // 我的名片：用户画像 Agent
  try {
    const res = await getUserProfile(1)
    if (res.code === 0 && res.data) {
      myCard.interests = res.data.interests ?? []
      myCard.labels = res.data.labels ?? []
    }
  } catch {
    // 忽略
  }
  // 已有联系人：关系运维 Agent
  try {
    const res = await getRelationGraph()
    if (res.code === 0 && res.data) {
      contacts.value = res.data.contacts || []
    }
  } catch {
    // 忽略
  }
  // 虚拟人物：情景模拟 Agent
  try {
    const res = await getVirtualCharacters()
    if (res.code === 0 && res.data) {
      virtuals.value = res.data
    }
  } catch {
    // 忽略
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}

.form-scroll {
  flex: 1;
  min-height: 0;
}

.form-card {
  margin: 24rpx 30rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 32rpx 28rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.form-section {
  margin-bottom: 36rpx;
}

.form-section:last-child {
  margin-bottom: 0;
}

.form-label {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
  display: block;
  margin-bottom: 18rpx;
}

.form-input {
  width: 100%;
  height: 80rpx;
  background: #f8f8f8;
  border-radius: 12rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #333;
  box-sizing: border-box;
}

.form-input--custom {
  margin-top: 16rpx;
}

.form-textarea {
  width: 100%;
  min-height: 140rpx;
  background: #f8f8f8;
  border-radius: 12rpx;
  padding: 18rpx 24rpx;
  font-size: 28rpx;
  color: #333;
  box-sizing: border-box;
}

/* picker */
.picker-box {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 80rpx;
  background: #f8f8f8;
  border-radius: 12rpx;
  padding: 0 24rpx;
}

.picker-box--small {
  margin-bottom: 16rpx;
}

.picker-value {
  font-size: 28rpx;
  color: #333;
}

.picker-placeholder {
  font-size: 28rpx;
  color: #c0c4cc;
}

.picker-arrow {
  font-size: 24rpx;
  color: #999;
}

/* 对方名片模式 */
.mode-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.mode-chip {
  flex: 1;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  background: #f3f4f6;
  border: 2rpx solid transparent;
}

.mode-chip--active {
  background: #ebf0ff;
  border-color: #5b8def;
}

.mode-chip-text {
  font-size: 28rpx;
  color: #555;
}

.mode-chip--active .mode-chip-text {
  color: #5b8def;
  font-weight: 600;
}

.select-panel {
  background: #fafbfc;
  border-radius: 12rpx;
  padding: 20rpx;
}

.sub-mode-row {
  display: flex;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.sub-mode-chip {
  padding: 10rpx 28rpx;
  border-radius: 24rpx;
  background: #fff;
  border: 2rpx solid #e5e7eb;
}

.sub-mode-chip--active {
  border-color: #5b8def;
  background: #ebf0ff;
}

.sub-mode-chip-text {
  font-size: 26rpx;
  color: #555;
}

.sub-mode-chip--active .sub-mode-chip-text {
  color: #5b8def;
  font-weight: 500;
}

.manage-link {
  margin-top: 8rpx;
  display: flex;
  justify-content: flex-end;
}

.manage-link-text {
  font-size: 26rpx;
  color: #5b8def;
}

.scan-result {
  background: #fafbfc;
  border-radius: 12rpx;
  padding: 20rpx;
}

.scan-result-text {
  font-size: 26rpx;
  color: #333;
}

.scan-result-text--empty {
  color: #c0c4cc;
}

/* 我的状态 */
.mood-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.mood-tags--custom {
  margin-top: 16rpx;
}

.mood-tag {
  padding: 12rpx 28rpx;
  border-radius: 32rpx;
  background: #f3f4f6;
  border: 2rpx solid transparent;
  transition: all 0.15s ease;
}

.mood-tag--active {
  color: #fff;
}

.mood-tag-text {
  font-size: 26rpx;
  color: #555;
}

.mood-tag--active .mood-tag-text {
  color: #fff;
  font-weight: 500;
}

.custom-mood {
  margin-top: 20rpx;
}

.custom-mood-toggle {
  display: inline-flex;
  padding: 12rpx 28rpx;
  border-radius: 32rpx;
  border: 2rpx dashed #cbd5e1;
  background: #fff;
}

.custom-mood-toggle-text {
  font-size: 26rpx;
  color: #9ca3af;
}

.custom-mood-input-row {
  display: flex;
  gap: 16rpx;
  margin-top: 16rpx;
  align-items: center;
}

.custom-mood-input {
  flex: 1;
}

.custom-mood-add {
  height: 80rpx;
  padding: 0 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  background: #5b8def;
}

.custom-mood-add-text {
  font-size: 28rpx;
  color: #fff;
  font-weight: 600;
}

/* 提交按钮 */
.submit-wrap {
  padding: 20rpx 30rpx 60rpx;
}

.submit-btn {
  width: 100%;
  height: 96rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 48rpx;
  background: linear-gradient(135deg, #2dd4bf, #14b8a6);
  box-shadow: 0 6rpx 20rpx rgba(20, 184, 166, 0.35);
  transition: opacity 0.2s;
}

.submit-btn--loading {
  opacity: 0.6;
}

.submit-btn-text {
  font-size: 32rpx;
  font-weight: 600;
  color: #fff;
  letter-spacing: 4rpx;
}
</style>

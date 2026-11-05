<template>
  <view class="wr-card">
    <view class="wr-head">
      <text class="wr-week">{{ report.weekKey }}</text>
      <text v-if="report.adopted" class="wr-adopted">已采纳下周计划 ✓</text>
    </view>

    <view class="wr-completion">
      <text class="wr-done">{{ report.completion?.tasksDone ?? 0 }}</text>
      <text class="wr-total">/{{ report.completion?.tasksTotal ?? 0 }} 任务</text>
      <text class="wr-sessions">训练 {{ report.completion?.sessionsScored ?? 0 }} 场</text>
      <text v-if="(report.completion?.focusSessions ?? 0) > 0" class="wr-focus">🎯×{{ report.completion.focusSessions }}</text>
    </view>

    <text class="wr-summary">{{ report.aiSummary }}</text>

    <view v-if="deltaChips.length" class="wr-deltas">
      <text
        v-for="d in deltaChips"
        :key="d.key"
        class="wr-delta"
        :class="d.up ? 'wr-delta--up' : 'wr-delta--down'"
      >{{ d.text }}</text>
    </view>

    <view v-for="(h, i) in report.highlights ?? []" :key="i" class="wr-highlight">
      <text class="wr-highlight-quote">“{{ h.quote }}”</text>
      <text class="wr-highlight-sub">{{ subSkillCn(h.subSkill) }}</text>
    </view>

    <!-- 下周草稿（仅最新一份可操作，P3-2） -->
    <view v-if="report.nextWeekDraft" class="wr-draft">
      <text class="wr-draft-title" @tap="draftOpen = !draftOpen">
        下周处方草稿 {{ draftOpen ? '▴' : '▾' }}
      </text>
      <template v-if="draftOpen">
        <text v-if="report.nextWeekDraft.summary" class="wr-draft-summary">{{ report.nextWeekDraft.summary }}</text>
        <view v-for="t in report.nextWeekDraft.tasks" :key="t.id" class="wr-draft-task">
          <text class="wr-draft-task-text">{{ TYPE_ICON[t.type] ?? '•' }} {{ t.title }}</text>
        </view>
        <view v-if="interactive" class="wr-draft-actions">
          <view class="wr-btn wr-btn--adopt" @tap="doAdopt">
            <text class="wr-btn-text">采纳为下周计划</text>
          </view>
          <view class="wr-btn wr-btn--refresh" @tap="doRefresh">
            <text class="wr-btn-text">重新生成</text>
          </view>
        </view>
      </template>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { WeeklyReport } from '@/types/plan'
import { subSkillCn } from '@/constants/subSkills'
import { adoptReport, refreshReportDraft } from '@/api/plan'

const props = defineProps<{ report: WeeklyReport; interactive?: boolean }>()
const emit = defineEmits<{ (e: 'changed'): void }>()

const draftOpen = ref(false)

const TYPE_ICON: Record<string, string> = { simulation: '🎬', realworld: '🌍', knowledge: '📖' }

const deltaChips = computed(() => {
  const out: { key: string; text: string; up: boolean }[] = []
  const deltas = props.report.deltas ?? {}
  for (const [k, v] of Object.entries(deltas)) {
    if (Math.abs(v) < 3) continue
    out.push({ key: k, text: `${subSkillCn(k)} ${v > 0 ? '↑' : '↓'}${Math.abs(v)}`, up: v > 0 })
  }
  return out
})

async function doAdopt() {
  try {
    const res = await adoptReport(props.report.weekKey)
    if (res.code === 0 && res.data.adopted) {
      uni.showToast({ title: '已采纳，周切换后生效', icon: 'success' })
      emit('changed')
    } else {
      uni.showToast({ title: (res.data as any)?.message || '采纳失败', icon: 'none' })
    }
  } catch {
    uni.showToast({ title: '网络异常', icon: 'none' })
  }
}

async function doRefresh() {
  uni.showModal({
    title: '重新生成草稿',
    content: '将基于最新训练数据重新生成下周处方草稿，当前草稿与采纳状态会被清除',
    success: async (r) => {
      if (!r.confirm) return
      try {
        const res = await refreshReportDraft(props.report.weekKey)
        if (res.code === 0 && res.data.status === 'ok') {
          uni.showToast({ title: '草稿已更新', icon: 'success' })
          emit('changed')
        } else {
          uni.showToast({ title: '生成失败，请稍后重试', icon: 'none' })
        }
      } catch {
        uni.showToast({ title: '网络异常', icon: 'none' })
      }
    },
  })
}
</script>

<style scoped>
.wr-card {
  width: 520rpx;
  flex-shrink: 0;
  background: #fff;
  border-radius: 16rpx;
  border: 2rpx solid #eef1f6;
  padding: 22rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-right: 16rpx;
}
.wr-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.wr-week {
  font-size: 26rpx;
  font-weight: 700;
  color: #333;
}
.wr-adopted {
  font-size: 20rpx;
  color: #2f855a;
}
.wr-completion {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
}
.wr-done {
  font-size: 40rpx;
  font-weight: bold;
  color: #5b8def;
}
.wr-total {
  font-size: 22rpx;
  color: #888;
}
.wr-sessions {
  font-size: 22rpx;
  color: #888;
  margin-left: auto;
}
.wr-focus {
  font-size: 22rpx;
  color: #3b6fd4;
}
.wr-summary {
  font-size: 24rpx;
  color: #555;
  line-height: 1.6;
}
.wr-deltas {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
}
.wr-delta {
  font-size: 20rpx;
  padding: 3rpx 14rpx;
  border-radius: 16rpx;
}
.wr-delta--up {
  color: #10b981;
  background: #e7f8f1;
}
.wr-delta--down {
  color: #f43f5e;
  background: #feecef;
}
.wr-highlight {
  background: #f8faf6;
  border-left: 5rpx solid #43b581;
  border-radius: 8rpx;
  padding: 12rpx 16rpx;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.wr-highlight-quote {
  font-size: 23rpx;
  color: #3c5140;
}
.wr-highlight-sub {
  font-size: 20rpx;
  color: #8a94a6;
}
.wr-draft {
  border-top: 2rpx dashed #e5e7eb;
  padding-top: 10rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.wr-draft-title {
  font-size: 24rpx;
  font-weight: 600;
  color: #5b8def;
}
.wr-draft-summary {
  font-size: 22rpx;
  color: #666;
  line-height: 1.5;
}
.wr-draft-task {
  padding: 4rpx 0;
}
.wr-draft-task-text {
  font-size: 22rpx;
  color: #444;
}
.wr-draft-actions {
  display: flex;
  gap: 12rpx;
  margin-top: 6rpx;
}
.wr-btn {
  padding: 10rpx 20rpx;
  border-radius: 28rpx;
}
.wr-btn--adopt {
  background: linear-gradient(135deg, #5b8def, #3b6fd4);
}
.wr-btn--refresh {
  background: #f3f4f6;
  border: 2rpx solid #e5e7eb;
}
.wr-btn-text {
  font-size: 22rpx;
  color: #fff;
  font-weight: 600;
}
.wr-btn--refresh .wr-btn-text {
  color: #374151;
}
</style>

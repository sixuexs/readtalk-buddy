<template>
  <view class="page-container">
    <!-- 顶部导航栏 -->
    <view class="top-bar">
      <text class="top-title">虚拟人物</text>
    </view>

    <scroll-view class="list-scroll" scroll-y>
      <!-- 新增表单 -->
      <view class="add-card">
        <text class="add-title">新增虚拟人物</text>
        <view class="add-row">
          <text class="add-label">姓名</text>
          <input class="add-input" v-model="form.name" placeholder="人物名" placeholder-style="color:#c0c4cc" maxlength="20" />
        </view>
        <view class="add-row">
          <text class="add-label">性格</text>
          <input class="add-input" v-model="form.personality" placeholder="如：乐观开朗" placeholder-style="color:#c0c4cc" maxlength="40" />
        </view>
        <view class="add-row">
          <text class="add-label">兴趣</text>
          <input class="add-input" v-model="form.interests" placeholder="如：阅读、跑步（顿号/逗号分隔）" placeholder-style="color:#c0c4cc" maxlength="100" />
        </view>
        <view class="add-row">
          <text class="add-label">标签</text>
          <input class="add-input" v-model="form.labels" placeholder="如：书虫、社牛（顿号/逗号分隔）" placeholder-style="color:#c0c4cc" maxlength="100" />
        </view>
        <view class="add-row">
          <text class="add-label">简介</text>
          <input class="add-input" v-model="form.description" placeholder="一句话简介（可选）" placeholder-style="color:#c0c4cc" maxlength="100" />
        </view>
        <view class="add-btn" @tap="handleCreate">
          <text class="add-btn-text">添加</text>
        </view>
      </view>

      <!-- 列表 -->
      <view class="list">
        <view v-if="!characters.length" class="list-empty">
          <text class="list-empty-text">暂无虚拟人物</text>
        </view>
        <view v-for="c in characters" :key="c.id" class="list-item">
          <view class="list-item-info">
            <text class="list-item-name">{{ c.name }}</text>
            <text class="list-item-sub">{{ c.personality || '未填性格' }}</text>
            <view v-if="c.interests.length || c.labels.length" class="list-item-tags">
              <text class="list-item-tag" v-for="t in [...c.interests, ...c.labels]" :key="t">{{ t }}</text>
            </view>
          </view>
          <view class="list-item-del" @tap="handleDelete(c)">
            <text class="list-item-del-text">删除</text>
          </view>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  getVirtualCharacters,
  createVirtualCharacter,
  deleteVirtualCharacter,
} from '@/api/simulation'
import type { VirtualCharacter } from '@/types/simulation'

const characters = ref<VirtualCharacter[]>([])

const form = reactive({
  name: '',
  personality: '',
  interests: '',
  labels: '',
  description: '',
})

function splitTags(input: string): string[] {
  return input
    .split(/[、,，;；\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

async function loadCharacters() {
  try {
    const res = await getVirtualCharacters()
    if (res.code === 0 && res.data) {
      characters.value = res.data
    }
  } catch {
    // 忽略
  }
}

async function handleCreate() {
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入姓名', icon: 'none' })
    return
  }
  try {
    await createVirtualCharacter({
      name: form.name.trim(),
      personality: form.personality.trim(),
      interests: splitTags(form.interests),
      labels: splitTags(form.labels),
      description: form.description.trim(),
    })
    uni.showToast({ title: '添加成功', icon: 'success' })
    form.name = ''
    form.personality = ''
    form.interests = ''
    form.labels = ''
    form.description = ''
    loadCharacters()
  } catch {
    uni.showToast({ title: '添加失败', icon: 'none' })
  }
}

function handleDelete(c: VirtualCharacter) {
  uni.showModal({
    title: '删除确认',
    content: `确定删除「${c.name}」吗？`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteVirtualCharacter(c.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        loadCharacters()
      } catch {
        uni.showToast({ title: '删除失败', icon: 'none' })
      }
    },
  })
}

onMounted(() => {
  loadCharacters()
})
</script>

<style scoped>
.page-container {
  min-height: 100vh;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
}

.top-bar {
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border-bottom: 1rpx solid #eee;
  flex-shrink: 0;
}

.top-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #5b8def;
}

.list-scroll {
  flex: 1;
  min-height: 0;
  padding-bottom: 40rpx;
}

.add-card {
  margin: 24rpx 30rpx;
  background: #fff;
  border-radius: 20rpx;
  padding: 28rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.add-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #333;
  display: block;
  margin-bottom: 20rpx;
}

.add-row {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}

.add-label {
  width: 100rpx;
  flex-shrink: 0;
  font-size: 26rpx;
  color: #555;
}

.add-input {
  flex: 1;
  height: 72rpx;
  background: #f8f8f8;
  border-radius: 12rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: #333;
  box-sizing: border-box;
}

.add-btn {
  margin-top: 16rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  background: #5b8def;
}

.add-btn-text {
  font-size: 28rpx;
  color: #fff;
  font-weight: 600;
}

.list {
  margin: 0 30rpx;
}

.list-empty {
  padding: 80rpx 0;
  text-align: center;
}

.list-empty-text {
  font-size: 26rpx;
  color: #bbb;
}

.list-item {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.list-item-info {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.list-item-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 6rpx;
}

.list-item-sub {
  font-size: 24rpx;
  color: #9ca3af;
  margin-bottom: 10rpx;
}

.list-item-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.list-item-tag {
  padding: 4rpx 16rpx;
  border-radius: 20rpx;
  background: #eff6ff;
  font-size: 22rpx;
  color: #2563eb;
}

.list-item-del {
  padding: 12rpx 24rpx;
  border-radius: 24rpx;
  background: #fef2f2;
}

.list-item-del-text {
  font-size: 24rpx;
  color: #ef4444;
}
</style>

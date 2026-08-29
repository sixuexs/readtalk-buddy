<template>
  <view class="profile-page">
    <!-- 顶部头像区域 -->
    <view class="avatar-section">
      <view class="avatar-wrapper" @click="handleAvatarClick">
        <!-- emoji 头像（已保存过名片时） -->
        <view
          v-if="userInfo.avatar"
          class="avatar-emoji-wrap"
          :style="{ backgroundColor: avatarBgColor }"
        >
          <text class="avatar-emoji">{{ userInfo.avatar }}</text>
        </view>
        <image v-else class="avatar" src="/static/logo.png" mode="aspectFill" />
      </view>
      <text class="user-name">{{ userInfo.name }}</text>
    </view>

    <!-- 功能菜单列表 -->
    <view class="menu-section">
      <view
        class="menu-item"
        v-for="item in menuList"
        :key="item.title"
        hover-class="menu-item--hover"
        @click="handleMenuClick(item)"
      >
        <text class="menu-title">{{ item.title }}</text>
      </view>
    </view>

    <CustomTabBar />
    <FloatingActionButton />
  </view>
</template>

<script setup lang="ts">
import { reactive, computed, onMounted } from 'vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import FloatingActionButton from '@/components/FloatingActionButton.vue'
import { getUserProfile } from '@/api/user'

// 名片页 emoji 头像的配色（与 namecard.vue 保持一致）
const AVATAR_EMOJIS = [
  '🐱', '🐶', '🦊', '🐻', '🐼', '🐨', '🦁', '🐰',
  '🐯', '🐸', '🐵', '🦄', '🐷', '🐮', '🐹', '🐔',
  '🐙', '🦋', '🐢', '🐬',
]
const AVATAR_BG = [
  '#FDE68A', '#BFDBFE', '#FBCFE8', '#C7D2FE', '#A7F3D0', '#FECACA',
  '#FED7AA', '#E9D5FF', '#FCA5A5', '#A5F3FC', '#FDE047', '#F0ABFC',
  '#FDBA74', '#BAE6FD', '#BBF7D0', '#F9A8D4', '#99F6E4', '#C4B5FD',
  '#86EFAC', '#93C5FD',
]

// 用户信息：从用户画像加载（未保存过名片时用默认值）
const userInfo = reactive({
  avatar: '',
  name: '用户名',
})

const avatarBgColor = computed(() => {
  const idx = AVATAR_EMOJIS.indexOf(userInfo.avatar)
  return AVATAR_BG[idx >= 0 ? idx : 0]
})

// 加载已保存的名片信息
async function loadProfile() {
  try {
    const res = await getUserProfile(1)
    if (res.code === 0 && res.data) {
      if (res.data.displayName) userInfo.name = res.data.displayName
      if (res.data.avatar) userInfo.avatar = res.data.avatar
    }
  } catch {
    // 接口不可用时保持默认
  }
}

// 功能菜单列表，key 用于区分菜单项点击逻辑
const menuList = [
  { title: '个人名片', key: 'business-card' },
  { title: '社交记录', key: 'social-record' },
  { title: '匿名反馈', key: 'feedback' },
  { title: '客服', key: 'service' },
  { title: '设置', key: 'settings' },
  { title: '退出登录', key: 'logout' },
]

// 头像点击：跳转名片页（头像/昵称在名片页维护）
function handleAvatarClick() {
  uni.navigateTo({ url: '/pages/namecard/namecard' })
}

// 菜单项点击：个人名片跳转、退出登录弹确认框，其余显示 Toast
function handleMenuClick(item: { title: string; key: string }) {
  if (item.key === 'business-card') {
    uni.navigateTo({ url: '/pages/namecard/namecard' })
    return
  }
  if (item.key === 'logout') {
    uni.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          uni.showToast({ title: '已退出', icon: 'none' })
        }
      },
    })
    return
  }
  uni.showToast({ title: item.title, icon: 'none' })
}

onMounted(() => {
  loadProfile()
})

</script>

<style scoped>
.profile-page {
  width: 100%;
  min-height: 100vh;
  background-color: #f5f5f5;
  padding-bottom: 140rpx;
}

/* ========== 头像区域 ========== */
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80rpx 0 60rpx;
  background-color: #ffffff;
}

.avatar-wrapper {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  overflow: hidden;
  margin-bottom: 24rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.08);
}

.avatar {
  width: 100%;
  height: 100%;
}

.avatar-emoji-wrap {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-emoji {
  font-size: 76rpx;
  line-height: 1;
}

.user-name {
  font-size: 32rpx;
  font-weight: 600;
  color: #333333;
}

/* ========== 菜单列表 ========== */
.menu-section {
  margin-top: 20rpx;
  background-color: #ffffff;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100rpx;
  position: relative;
}

.menu-item + .menu-item::before {
  content: '';
  position: absolute;
  top: 0;
  left: 30rpx;
  right: 30rpx;
  height: 1rpx;
  background-color: #f0f0f0;
}

.menu-item--hover {
  background-color: #f5f5f5;
}

.menu-title {
  font-size: 30rpx;
  color: #333333;
}

</style>

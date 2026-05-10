<template>
  <view class="profile-page">
    <!-- 顶部头像区域 -->
    <view class="avatar-section">
      <view class="avatar-wrapper" @click="handleAvatarClick">
        <image class="avatar" :src="userInfo.avatar" mode="aspectFill" />
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
import { reactive } from 'vue'
import CustomTabBar from '@/components/CustomTabBar.vue'
import FloatingActionButton from '@/components/FloatingActionButton.vue'

// 用户信息（当前为静态数据，后续替换为接口获取）
const userInfo = reactive({
  avatar: '/static/logo.png',
  name: '用户名',
})

// 功能菜单列表，key 用于区分菜单项点击逻辑
const menuList = [
  { title: '个人名片', key: 'business-card' },
  { title: '社交记录', key: 'social-record' },
  { title: '匿名反馈', key: 'feedback' },
  { title: '客服', key: 'service' },
  { title: '设置', key: 'settings' },
  { title: '退出登录', key: 'logout' },
]

// 头像点击：触发更换头像（当前为占位实现）
function handleAvatarClick() {
  uni.showToast({ title: '更换头像', icon: 'none' })
}

// 菜单项点击：退出登录弹出确认框，其余显示 Toast
function handleMenuClick(item: { title: string; key: string }) {
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

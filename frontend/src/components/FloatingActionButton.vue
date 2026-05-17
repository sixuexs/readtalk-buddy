<template>
  <!-- 遮罩层（独立于按钮容器，确保层级在按钮之下） -->
  <view
    class="fab-overlay"
    :class="{ 'fab-overlay--visible': open }"
    @click="open = false"
  />

  <!-- 按钮容器 -->
  <view class="fab-container">
    <!-- 子按钮 -->
    <view
      v-for="item in subButtons"
      :key="item.key"
      class="sub-btn"
      :class="[
        `sub-btn--${item.key}`,
        { 'sub-btn--visible': open }
      ]"
      @click="handleSubClick(item)"
    >
      <view class="sub-btn-icon" :style="{ backgroundColor: item.color }">
        <text class="sub-btn-icon-text">{{ item.icon }}</text>
      </view>
      <text class="sub-btn-label">{{ item.label }}</text>
    </view>

    <!-- 主按钮 -->
    <view
      class="fab"
      :class="{ 'fab--active': open }"
      @click="open = !open"
    >
      <text class="fab-icon">+</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

// 悬浮按钮展开/收起状态
const open = ref(false)

// 监听 tab 切换，自动收起展开状态（带 CSS transition 自然过渡）
function collapseOpen() {
  open.value = false
}
uni.$on('tab-switch', collapseOpen)
onUnmounted(() => {
  uni.$off('tab-switch', collapseOpen)
})

// 子按钮列表：扫码连接、沟通辅助
const subButtons = [
  { key: 'scan', label: '扫码连接', icon: '扫', color: '#4A90D9' },
  { key: 'assist', label: '沟通辅助', icon: '辅', color: '#5B8DEF' },
]

// 子按钮点击：收起菜单并显示 Toast 提示（后续替换为实际跳转逻辑）
function handleSubClick(item: { key: string; label: string }) {
  open.value = false
  uni.showToast({ title: item.label, icon: 'none' })
}
</script>

<style scoped>
/* ========== 遮罩 ========== */
.fab-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.35);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.3s ease;
  z-index: 199;
}

.fab-overlay--visible {
  opacity: 1;
  pointer-events: auto;
}

/* ========== 按钮容器 ========== */
.fab-container {
  position: fixed;
  right: 40rpx;
  bottom: 200rpx;
  width: 100rpx;
  height: 100rpx;
  z-index: 200;
}

/* ========== 子按钮 ========== */
.sub-btn {
  position: absolute;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) scale(0.3);
  opacity: 0;
  transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  pointer-events: none;
}

.sub-btn--visible {
  opacity: 1;
  pointer-events: auto;
}

.sub-btn--scan.sub-btn--visible {
  transform: translate(-60rpx, -180rpx) scale(1);
  transition-delay: 0.04s;
}

.sub-btn--assist.sub-btn--visible {
  transform: translate(-150rpx, -70rpx) scale(1);
  transition-delay: 0.1s;
}

.sub-btn-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.15);
}

.sub-btn-icon-text {
  font-size: 32rpx;
  color: #ffffff;
  font-weight: 600;
}

.sub-btn-label {
  font-size: 22rpx;
  color: #666666;
  white-space: nowrap;
}

/* ========== 主按钮 ========== */
.fab {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: linear-gradient(135deg, #5B8DEF, #3B6FD4);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(59, 111, 212, 0.4);
  transition: all 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
  z-index: 201;
}

.fab--active {
  width: 72%;
  height: 72%;
  top: 14%;
  left: 14%;
  box-shadow: 0 4rpx 12rpx rgba(59, 111, 212, 0.3);
}

.fab-icon {
  font-size: 52rpx;
  color: #ffffff;
  font-weight: 300;
  line-height: 1;
  transition: transform 0.35s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.fab--active .fab-icon {
  transform: rotate(135deg);
}
</style>

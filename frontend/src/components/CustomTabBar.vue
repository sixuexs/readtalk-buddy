<template>
  <view class="tab-bar-wrapper">
    <view class="tab-bar">
      <view
        v-for="(item, index) in tabList"
        :key="index"
        class="tab-item"
        :class="{ 'tab-item--middle': item.isMiddle }"
        @click="handleTabClick(item)"
      >
        <view v-if="item.isMiddle" class="middle-btn" :class="{ 'middle-btn--active': currentIndex === 2 }">
          <view class="middle-btn-icon-wrap">
            <image
              class="middle-btn-icon"
              :src="item.activeSrc"
              mode="aspectFit"
            />
          </view>
          <text class="middle-btn-text">{{ item.text }}</text>
        </view>
        <template v-else>
          <image
            class="tab-icon"
            :class="{ 'tab-icon--active': currentIndex === index }"
            :src="currentIndex === index ? item.activeSrc : item.normalSrc"
            mode="aspectFit"
          />
          <text
            class="tab-text"
            :style="{ color: currentIndex === index ? selectedColor : color }"
          >
            {{ item.text }}
          </text>
        </template>
      </view>
    </view>
    <view class="safe-area-placeholder" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'

// 底部导航栏配置：页面路径、文字、是否中间凸起按钮、图标路径
const tabList = [
  {
    pagePath: '/pages/home/home',
    text: '首页',
    isMiddle: false,
    normalSrc: '/static/bottom_bar/首页.png',
    activeSrc: '/static/bottom_bar/首页.png',
  },
  {
    pagePath: '/pages/social/social',
    text: '社交记录',
    isMiddle: false,
    normalSrc: '/static/bottom_bar/社交记录.png',
    activeSrc: '/static/bottom_bar/社交记录.png',
  },
  {
    pagePath: '/pages/relation/relation',
    text: '关系图谱',
    isMiddle: true,
    normalSrc: '/static/bottom_bar/关系图谱.png',
    activeSrc: '/static/bottom_bar/关系图谱.png',
  },
  {
    pagePath: '/pages/simulation/simulation',
    text: '情景模拟',
    isMiddle: false,
    normalSrc: '/static/bottom_bar/情景模拟.png',
    activeSrc: '/static/bottom_bar/情景模拟.png',
  },
  {
    pagePath: '/pages/profile/profile',
    text: '个人中心',
    isMiddle: false,
    normalSrc: '/static/bottom_bar/个人中心.png',
    activeSrc: '/static/bottom_bar/个人中心.png',
  },
]

const color = '#999999'
const selectedColor = '#007AFF'

// 当前激活的 tab 索引
const currentIndex = ref(0)

// 获取当前页面路由，用于初始化时同步激活状态
const pages = getCurrentPages()
const currentRoute = pages[pages.length - 1]?.route || ''

// 根据当前页面路由同步高亮的 tab 索引
function syncActiveIndex() {
  const pages = getCurrentPages()
  const route = pages[pages.length - 1]?.route
  const idx = tabList.findIndex((item) => item.pagePath === `/${route}`)
  if (idx !== -1) {
    currentIndex.value = idx
  }
}

// 组件初始化时同步一次
syncActiveIndex()

// 页面显示时重新同步（处理 switchTab 跳转回来的场景）
onShow(() => {
  syncActiveIndex()
})

// 点击 tab 项，先通知全局收起 FAB，等过渡动画播完再跳转
let isSwitching = false
function handleTabClick(item: typeof tabList[0]) {
  if (isSwitching) return
  isSwitching = true
  uni.$emit('tab-switch')
  // 延迟 300ms 让 FAB 的 CSS transition（0.35s）有足够时间播完收起动画
  setTimeout(() => {
    uni.switchTab({
      url: item.pagePath,
    })
    isSwitching = false
  }, 300)
}
</script>

<style scoped>
/* ========== 底部导航栏容器：固定在视口底部 ========== */
.tab-bar-wrapper {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 999;
}

.tab-bar {
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: 100rpx;
  background-color: #ffffff;
  box-shadow: 0 -2rpx 10rpx rgba(0, 0, 0, 0.06);
  position: relative;
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  height: 100%;
}

.tab-item--middle {
  justify-content: flex-end;
}

.tab-icon {
  width: 44rpx;
  height: 44rpx;
  margin-bottom: 12rpx;
  transition: transform 0.2s ease;
}

.tab-icon--active {
  transform: translateY(-6rpx);
}

.tab-text {
  font-size: 20rpx;
  line-height: 1;
}

.middle-btn {
  position: absolute;
  top: -30rpx;
  width: 110rpx;
  height: 110rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #B3D9FF, #aae2b4);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 10;
  transition: box-shadow 0.3s ease;
}

.middle-btn--active {
  box-shadow: 0 6rpx 20rpx rgba(0, 122, 255, 0.4);
}

.middle-btn-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}

.middle-btn-icon {
  width: 44rpx;
  height: 44rpx;
  margin-right: -6rpx;
}

.middle-btn-text {
  font-size: 18rpx;
  color: #ffffff;
  margin-top: 2rpx;
  line-height: 1;
}

.safe-area-placeholder {
  height: constant(safe-area-inset-bottom);
  height: env(safe-area-inset-bottom);
  background-color: #ffffff;
}
</style>

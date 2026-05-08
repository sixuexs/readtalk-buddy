<template>
  <view class="page-container">
    <text class="page-title">关系图谱</text>
    <CustomTabBar />
    <FloatingActionButton />
  </view>
</template>

<script setup lang="ts">
import CustomTabBar from '@/components/CustomTabBar.vue'
import FloatingActionButton from '@/components/FloatingActionButton.vue'
// 关系图谱 - 占位页面，待后续填充业务内容
</script>

<style scoped>
.page-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100vh;
  padding-bottom: 140rpx;
}

.page-title {
  font-size: 36rpx;
  color: #333;
}
</style>

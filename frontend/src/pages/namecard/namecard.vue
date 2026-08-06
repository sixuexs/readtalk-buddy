<template>
  <view class="namecard-page">
    <!-- 卡片容器 -->
    <view class="card">
      <!-- 二维码区域 -->
      <view class="qr-section">
        <image
          class="qr-image"
          :src="qrCodeUrl"
          mode="aspectFit"
          @error="onQrError"
        />
        <text class="qr-hint">扫码添加好友</text>
      </view>

      <!-- 分隔线 -->
      <view class="divider" />

      <!-- 表单区域 -->
      <view class="form-section">
        <!-- 头像预览 -->
        <view class="form-row form-row--avatar">
          <text class="form-label">头像</text>
          <view class="avatar-preview">
            <image
              class="avatar-img"
              :src="form.avatarUrl || '/static/logo.png'"
              mode="aspectFill"
            />
          </view>
        </view>

        <!-- 头像 URL 输入 -->
        <view class="form-row">
          <text class="form-label">头像URL</text>
          <input
            class="form-input"
            v-model="form.avatarUrl"
            placeholder="输入头像链接"
            placeholder-style="color:#c0c4cc"
          />
        </view>

        <!-- 昵称 -->
        <view class="form-row">
          <text class="form-label">昵称</text>
          <input
            class="form-input"
            v-model="form.displayName"
            placeholder="输入昵称"
            placeholder-style="color:#c0c4cc"
          />
        </view>

        <!-- 状态 -->
        <view class="form-row">
          <text class="form-label">状态</text>
          <input
            class="form-input"
            v-model="form.status"
            placeholder="如：阅读中 - 《三体》"
            placeholder-style="color:#c0c4cc"
          />
        </view>

        <!-- 简介 -->
        <view class="form-row form-row--bio">
          <text class="form-label">简介</text>
          <textarea
            class="form-textarea"
            v-model="form.biography"
            placeholder="介绍一下自己..."
            placeholder-style="color:#c0c4cc"
            :maxlength="200"
            auto-height
          />
        </view>
      </view>
    </view>

    <!-- 操作按钮 -->
    <view class="actions">
      <view class="btn btn--primary" hover-class="btn--hover" @click="handleSave">
        保存信息
      </view>
      <view class="btn btn--secondary" hover-class="btn--hover" @click="handleSaveImage">
        保存为图片
      </view>
    </view>

    <!-- 隐藏 canvas 用于导出图片 -->
    <canvas
      canvas-id="namecardCanvas"
      id="namecardCanvas"
      class="export-canvas"
    />
  </view>
</template>

<script setup lang="ts">
import { reactive, computed, onMounted, ref } from 'vue'
import { getUserProfile, updateUserProfile } from '@/api/user'
import type { UserProfile } from '@/types/user'

// ── 表单数据 ──
const form = reactive<UserProfile>({
  userId: 1,
  displayName: '',
  biography: '',
  status: '',
  avatarUrl: '',
})

const loading = ref(false)
const qrLoadFailed = ref(false)

// ── 二维码 URL ──
const qrCodeUrl = computed(() => {
  const data = encodeURIComponent(`${form.displayName || '未命名'}_${form.userId}`)
  return `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${data}`
})

function onQrError() {
  qrLoadFailed.value = true
}

// ── 加载用户档案 ──
async function loadProfile() {
  try {
    const res = await getUserProfile(1)
    if (res.code === 0 && res.data) {
      form.userId = res.data.userId ?? 1
      form.displayName = res.data.displayName ?? ''
      form.biography = res.data.biography ?? ''
      form.status = res.data.status ?? ''
      form.avatarUrl = res.data.avatarUrl ?? ''
    }
  } catch {
    // 接口不可用时使用默认值
    form.displayName = form.displayName || '未命名用户'
  }
}

// ── 保存信息 ──
async function handleSave() {
  if (!form.displayName.trim()) {
    uni.showToast({ title: '请输入昵称', icon: 'none' })
    return
  }

  loading.value = true
  try {
    await updateUserProfile({
      userId: form.userId,
      displayName: form.displayName.trim(),
      biography: form.biography.trim(),
      status: form.status.trim(),
    })
    uni.showToast({ title: '保存成功', icon: 'success' })
  } catch {
    uni.showToast({ title: '保存失败，请重试', icon: 'none' })
  } finally {
    loading.value = false
  }
}

// ── 保存为图片 ──
function handleSaveImage() {
  uni.showLoading({ title: '生成中...' })

  const qrSrc = qrCodeUrl.value

  uni.getImageInfo({
    src: qrSrc,
    success: (imgInfo) => {
      drawAndExport(imgInfo.path)
    },
    fail: () => {
      uni.hideLoading()
      uni.showToast({ title: '二维码加载失败', icon: 'none' })
    },
  })
}

function drawAndExport(qrLocalPath: string) {
  const ctx = uni.createCanvasContext('namecardCanvas')

  // 白底
  ctx.setFillStyle('#ffffff')
  ctx.fillRect(0, 0, 600, 800)

  // 二维码居中（y 偏上）
  ctx.drawImage(qrLocalPath, 200, 80, 200, 200)

  // 装饰线
  ctx.setStrokeStyle('#e0e0e0')
  ctx.setLineWidth(1)
  ctx.beginPath()
  ctx.moveTo(60, 320)
  ctx.lineTo(540, 320)
  ctx.stroke()

  // 昵称
  ctx.setFillStyle('#1a1a1a')
  ctx.setFontSize(34)
  ctx.setTextAlign('center')
  ctx.fillText(form.displayName || '未命名', 300, 380)

  // 状态
  ctx.setFillStyle('#888888')
  ctx.setFontSize(24)
  ctx.fillText(form.status || '', 300, 430)

  // 签名水印
  ctx.setFillStyle('#cccccc')
  ctx.setFontSize(18)
  ctx.fillText('阅谈智伴 · 个人名片', 300, 750)

  ctx.draw(false, () => {
    uni.canvasToTempFilePath({
      canvasId: 'namecardCanvas',
      width: 600,
      height: 800,
      success: (res) => {
        uni.hideLoading()
        uni.saveImageToPhotosAlbum({
          filePath: res.tempFilePath,
          success: () => {
            uni.showToast({ title: '已保存到相册', icon: 'success' })
          },
          fail: () => {
            // 权限失败时预览图片
            uni.previewImage({ urls: [res.tempFilePath] })
          },
        })
      },
      fail: () => {
        uni.hideLoading()
        uni.showToast({ title: '图片生成失败', icon: 'none' })
      },
    })
  })
}

// ── 生命周期 ──
onMounted(() => {
  loadProfile()
})
</script>

<style scoped>
.namecard-page {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f5ff 0%, #f5f5f5 40%);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40rpx 32rpx;
  padding-bottom: calc(60rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

/* ========== 卡片 ========== */
.card {
  width: 100%;
  max-width: 680rpx;
  background: #ffffff;
  border-radius: 24rpx;
  box-shadow: 0 8rpx 32rpx rgba(59, 130, 246, 0.1);
  overflow: hidden;
}

/* ========== 二维码区域 ========== */
.qr-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 0 32rpx;
}

.qr-image {
  width: 240rpx;
  height: 240rpx;
  border-radius: 16rpx;
  background: #f5f5f5;
}

.qr-hint {
  margin-top: 20rpx;
  font-size: 24rpx;
  color: #999999;
}

/* ========== 分隔线 ========== */
.divider {
  height: 1rpx;
  margin: 0 40rpx;
  background: linear-gradient(90deg, transparent, #e5e7eb 20%, #e5e7eb 80%, transparent);
}

/* ========== 表单区域 ========== */
.form-section {
  padding: 32rpx 40rpx 40rpx;
}

.form-row {
  display: flex;
  align-items: center;
  margin-bottom: 28rpx;
  min-height: 72rpx;
}

.form-row--avatar {
  flex-direction: column;
  align-items: flex-start;
  gap: 16rpx;
}

.form-row--bio {
  align-items: flex-start;
}

.form-label {
  width: 130rpx;
  flex-shrink: 0;
  font-size: 28rpx;
  font-weight: 500;
  color: #374151;
  line-height: 72rpx;
}

.form-row--bio .form-label {
  line-height: 44rpx;
  padding-top: 8rpx;
}

.form-input {
  flex: 1;
  height: 72rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: #1f2937;
  background: #f9fafb;
  border: 1rpx solid #e5e7eb;
  border-radius: 12rpx;
}

.form-textarea {
  flex: 1;
  min-height: 140rpx;
  padding: 14rpx 20rpx;
  font-size: 28rpx;
  color: #1f2937;
  background: #f9fafb;
  border: 1rpx solid #e5e7eb;
  border-radius: 12rpx;
  line-height: 1.6;
}

/* ========== 头像预览 ========== */
.avatar-preview {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  overflow: hidden;
  border: 3rpx solid #e5e7eb;
  background: #f3f4f6;
}

.avatar-img {
  width: 100%;
  height: 100%;
}

/* ========== 按钮 ========== */
.actions {
  width: 100%;
  max-width: 680rpx;
  margin-top: 36rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 88rpx;
  border-radius: 16rpx;
  font-size: 30rpx;
  font-weight: 600;
}

.btn--primary {
  color: #ffffff;
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  box-shadow: 0 4rpx 16rpx rgba(59, 130, 246, 0.35);
}

.btn--secondary {
  color: #374151;
  background: #f3f4f6;
  border: 1rpx solid #e5e7eb;
}

.btn--hover {
  opacity: 0.85;
}

/* ========== 隐藏 canvas ========== */
.export-canvas {
  position: fixed;
  left: -9999px;
  top: -9999px;
  width: 600px;
  height: 800px;
}
</style>

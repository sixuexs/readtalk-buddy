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
        <!-- 头像 + 昵称/状态：左右结构，右侧上下结构 -->
        <view class="profile-head">
          <view class="avatar-wrap" @tap="previewAvatar">
            <view class="avatar-circle" :style="{ backgroundColor: avatarBgColor }">
              <text class="avatar-emoji">{{ form.avatar || '🐱' }}</text>
            </view>
          </view>
          <view class="profile-head-right">
            <input
              class="nick-input"
              v-model="form.displayName"
              placeholder="输入昵称"
              placeholder-style="color:#c0c4cc"
              maxlength="20"
            />
            <input
              class="status-input"
              v-model="form.status"
              placeholder="如：阅读中 - 《三体》"
              placeholder-style="color:#c0c4cc"
              maxlength="40"
            />
          </view>
        </view>

        <!-- 性格 -->
        <view class="form-row">
          <text class="form-label">性格</text>
          <input
            class="form-input"
            v-model="form.personality"
            placeholder="如：乐观开朗、慢热"
            placeholder-style="color:#c0c4cc"
            maxlength="40"
          />
        </view>

        <!-- 爱好 -->
        <view class="form-row">
          <text class="form-label">爱好</text>
          <input
            class="form-input"
            v-model="interestsText"
            placeholder="如：阅读、跑步、旅行（顿号/逗号分隔）"
            placeholder-style="color:#c0c4cc"
            maxlength="100"
          />
        </view>

        <!-- 标签 -->
        <view class="form-row form-row--tags">
          <text class="form-label">标签</text>
          <view class="tag-list">
            <view
              v-for="t in presetLabels"
              :key="t"
              class="tag"
              :class="{ 'tag--active': isLabelSelected(t) }"
              @tap="toggleLabel(t)"
            >
              <text class="tag-text">{{ t }}</text>
            </view>
            <view class="tag tag--add" @tap="openCustomTag">
              <text class="tag-add-icon">＋</text>
            </view>
          </view>
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

    <!-- 头像全屏放大预览 -->
    <view v-if="avatarPreviewVisible" class="fullscreen-mask" @tap="avatarPreviewVisible = false">
      <view
        class="fullscreen-avatar"
        :style="{ backgroundColor: avatarBgColor }"
      >
        <text class="fullscreen-avatar-emoji">{{ form.avatar || '🐱' }}</text>
      </view>
      <view class="fullscreen-switch" @tap.stop="openAvatarPicker">
        <text class="fullscreen-switch-text">切换头像</text>
      </view>
    </view>

    <!-- 头像选择弹层 -->
    <view v-if="avatarPickerVisible" class="picker-mask" @tap="avatarPickerVisible = false">
      <view class="picker-panel" @tap.stop>
        <text class="picker-title">选择头像</text>
        <view class="avatar-grid">
          <view
            v-for="(e, i) in AVATAR_EMOJIS"
            :key="e"
            class="avatar-grid-item"
            :class="{ 'avatar-grid-item--active': form.avatar === e }"
            @tap="selectAvatar(e)"
          >
            <view class="avatar-grid-circle" :style="{ backgroundColor: AVATAR_BG[i % AVATAR_BG.length] }">
              <text class="avatar-grid-emoji">{{ e }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 自定义标签弹层 -->
    <view v-if="customTagVisible" class="picker-mask" @tap="customTagVisible = false">
      <view class="picker-panel picker-panel--custom" @tap.stop>
        <text class="picker-title">自定义标签</text>
        <input
          class="custom-tag-input"
          v-model="customTagInput"
          placeholder="输入标签，如：斜杠青年"
          placeholder-style="color:#c0c4cc"
          maxlength="12"
        />
        <view class="custom-tag-actions">
          <view class="btn btn--secondary btn--small" @click="customTagVisible = false">
            <text class="btn-text">取消</text>
          </view>
          <view class="btn btn--primary btn--small" @click="addCustomTag">
            <text class="btn-text">确定</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, computed, onMounted, ref } from 'vue'
import { getUserProfile, updateUserProfile } from '@/api/user'
import type { UserProfile } from '@/types/user'

// ── 预置头像（emoji）与配色 ──
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

// ── 预置标签 ──
const presetLabels = [
  '书虫', '科幻迷', '摄影', '旅行', '健身',
  '音乐', '电影', '游戏', '美食', '萌宠',
]

// ── 表单数据 ──
const form = reactive<UserProfile>({
  userId: 1,
  displayName: '',
  biography: '',
  status: '',
  avatar: '🐱',
  personality: '',
  interests: [],
  labels: [],
})

const loading = ref(false)
const qrLoadFailed = ref(false)

// 爱好输入框：展示时合并为顿号分隔文本
const interestsText = ref('')

// 头像交互状态
const avatarPreviewVisible = ref(false)
const avatarPickerVisible = ref(false)

// 自定义标签状态
const customTagVisible = ref(false)
const customTagInput = ref('')

// 当前头像背景色
const avatarBgColor = computed(() => {
  const idx = AVATAR_EMOJIS.indexOf(form.avatar)
  return AVATAR_BG[idx >= 0 ? idx : 0]
})

// ── 二维码 URL ──
const qrCodeUrl = computed(() => {
  const data = encodeURIComponent(`${form.displayName || '未命名'}_${form.userId}`)
  return `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${data}`
})

function onQrError() {
  qrLoadFailed.value = true
}

// ── 头像交互 ──
function previewAvatar() {
  avatarPreviewVisible.value = true
}

function openAvatarPicker() {
  avatarPreviewVisible.value = false
  avatarPickerVisible.value = true
}

function selectAvatar(emoji: string) {
  form.avatar = emoji
  avatarPickerVisible.value = false
}

// ── 标签交互 ──
function isLabelSelected(label: string): boolean {
  return form.labels.includes(label)
}

function toggleLabel(label: string) {
  const idx = form.labels.indexOf(label)
  if (idx >= 0) {
    form.labels.splice(idx, 1)
  } else {
    form.labels.push(label)
  }
}

function openCustomTag() {
  customTagInput.value = ''
  customTagVisible.value = true
}

function addCustomTag() {
  const tag = customTagInput.value.trim()
  if (!tag) {
    uni.showToast({ title: '请输入标签', icon: 'none' })
    return
  }
  if (!form.labels.includes(tag)) {
    form.labels.push(tag)
  }
  customTagVisible.value = false
  customTagInput.value = ''
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
      form.avatar = res.data.avatar || '🐱'
      form.personality = res.data.personality ?? ''
      form.interests = res.data.interests ?? []
      form.labels = res.data.labels ?? []
      interestsText.value = form.interests.join('、')
    }
  } catch {
    // 接口不可用时使用默认值
    form.displayName = form.displayName || '未命名用户'
  }
}

// ── 保存信息 ──
function parseInterests(text: string): string[] {
  return text
    .split(/[、,，;；\s]+/)
    .map((s) => s.trim())
    .filter(Boolean)
}

async function handleSave() {
  if (!form.displayName.trim()) {
    uni.showToast({ title: '请输入昵称', icon: 'none' })
    return
  }

  const interests = parseInterests(interestsText.value)

  loading.value = true
  try {
    const res = await updateUserProfile({
      userId: form.userId,
      displayName: form.displayName.trim(),
      biography: form.biography.trim(),
      status: form.status.trim(),
      avatar: form.avatar,
      personality: form.personality.trim(),
      interests,
      labels: form.labels,
    })
    // 后端业务失败（如 code != 0）也视为保存失败，不假成功
    if (res.code !== 0) {
      uni.showToast({ title: '保存失败，请重试', icon: 'none' })
      return
    }
    form.interests = interests
    interestsText.value = interests.join('、')
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
  const W = 600
  const H = 800

  // 白底
  ctx.setFillStyle('#ffffff')
  ctx.fillRect(0, 0, W, H)

  // ── 头像：彩色圆底 + emoji ──
  const avatarIdx = AVATAR_EMOJIS.indexOf(form.avatar)
  const bg = AVATAR_BG[avatarIdx >= 0 ? avatarIdx : 0]
  const cx = 300
  const cy = 95
  const r = 52
  ctx.setFillStyle(bg)
  ctx.beginPath()
  ctx.arc(cx, cy, r, 0, Math.PI * 2)
  ctx.fill()

  // emoji 头像（旧版 canvas 可能无法渲染 emoji，空头像回退昵称首字）
  const avatarChar = form.avatar && form.avatar.trim() ? form.avatar : (form.displayName || '阅').charAt(0)
  ctx.setFillStyle('#1f2937')
  ctx.setFontSize(52)
  ctx.setTextAlign('center')
  ctx.setTextBaseline('middle')
  ctx.fillText(avatarChar, cx, cy)

  // ── 昵称 ──
  ctx.setFillStyle('#1a1a1a')
  ctx.setFontSize(32)
  ctx.setTextAlign('center')
  ctx.setTextBaseline('normal')
  ctx.fillText(form.displayName || '未命名', 300, 185)

  // ── 状态 ──
  ctx.setFillStyle('#888888')
  ctx.setFontSize(22)
  ctx.fillText(form.status || '', 300, 225)

  // ── 性格 ──
  let y = 265
  ctx.setTextAlign('left')
  ctx.setFontSize(22)
  if (form.personality && form.personality.trim()) {
    ctx.setFillStyle('#666666')
    ctx.fillText('性格：' + form.personality.trim(), 60, y)
    y += 40
  }

  // ── 爱好标签 ──
  const interests = parseInterests(interestsText.value)
  if (interests.length) {
    y = drawChips(ctx, '爱好：', interests, y, '#EFF6FF', '#2563EB')
  }

  // ── 标签 ──
  if (form.labels.length) {
    y = drawChips(ctx, '标签：', form.labels, y, '#ECFDF5', '#059669')
  }

  // ── 分隔线 ──
  const dividerY = 400
  ctx.setStrokeStyle('#e0e0e0')
  ctx.setLineWidth(1)
  ctx.beginPath()
  ctx.moveTo(60, dividerY)
  ctx.lineTo(540, dividerY)
  ctx.stroke()

  // ── 二维码（分隔线下方居中） ──
  ctx.drawImage(qrLocalPath, 200, 430, 200, 200)

  // ── 水印 ──
  ctx.setFillStyle('#cccccc')
  ctx.setFontSize(18)
  ctx.setTextAlign('center')
  ctx.fillText('阅谈智伴 · 个人名片', 300, 750)

  ctx.draw(false, () => {
    uni.canvasToTempFilePath({
      canvasId: 'namecardCanvas',
      width: W,
      height: H,
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

/** 在画布上绘制一组标签/爱好，返回下一行的 y 坐标 */
function drawChips(
  ctx: UniApp.CanvasContext,
  label: string,
  items: string[],
  startY: number,
  bgColor: string,
  fgColor: string,
): number {
  const left = 60
  const right = 540
  let x = left
  let y = startY
  const lineHeight = 40
  const chipGap = 10

  // 分类前缀
  ctx.setFillStyle('#666666')
  ctx.setFontSize(22)
  ctx.setTextAlign('left')
  ctx.setTextBaseline('normal')
  ctx.fillText(label, x, y)
  x += ctx.measureText(label).width + 10

  for (const item of items) {
    const text = item
    const chipW = 24 + ctx.measureText(text).width
    if (x + chipW > right) {
      x = left
      y += lineHeight
    }
    // 圆角胶囊（用圆角矩形近似）
    roundRect(ctx, x, y - 22, chipW, 30, 15, bgColor)
    ctx.setFillStyle(fgColor)
    ctx.setFontSize(20)
    ctx.setTextAlign('left')
    ctx.fillText(text, x + 12, y)
    x += chipW + chipGap
  }
  return y + lineHeight
}

/** 简单圆角矩形（旧版 canvas 无 roundRect） */
function roundRect(
  ctx: UniApp.CanvasContext,
  x: number,
  y: number,
  w: number,
  h: number,
  r: number,
  color: string,
) {
  ctx.setFillStyle(color)
  ctx.beginPath()
  ctx.moveTo(x + r, y)
  ctx.arcTo(x + w, y, x + w, y + h, r)
  ctx.arcTo(x + w, y + h, x, y + h, r)
  ctx.arcTo(x, y + h, x, y, r)
  ctx.arcTo(x, y, x + w, y, r)
  ctx.closePath()
  ctx.fill()
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
  padding: 40rpx 0 28rpx;
}

.qr-image {
  width: 220rpx;
  height: 220rpx;
  border-radius: 16rpx;
  background: #f5f5f5;
}

.qr-hint {
  margin-top: 16rpx;
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

/* ========== 头像 + 昵称/状态（左右结构） ========== */
.profile-head {
  display: flex;
  align-items: center;
  margin-bottom: 32rpx;
}

.avatar-wrap {
  flex-shrink: 0;
  margin-right: 28rpx;
}

.avatar-circle {
  width: 128rpx;
  height: 128rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 4rpx solid #ffffff;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.12);
}

.avatar-emoji {
  font-size: 64rpx;
  line-height: 1;
}

.profile-head-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.nick-input,
.status-input {
  height: 72rpx;
  padding: 0 20rpx;
  font-size: 28rpx;
  color: #1f2937;
  background: #f9fafb;
  border: 1rpx solid #e5e7eb;
  border-radius: 12rpx;
}

.nick-input {
  font-size: 32rpx;
  font-weight: 600;
}

/* ========== 通用表单行 ========== */
.form-row {
  display: flex;
  align-items: center;
  margin-bottom: 28rpx;
  min-height: 72rpx;
}

.form-row--bio {
  align-items: flex-start;
}

.form-row--tags {
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

.form-row--bio .form-label,
.form-row--tags .form-label {
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

/* ========== 标签 ========== */
.tag-list {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  align-items: center;
}

.tag {
  padding: 12rpx 28rpx;
  border-radius: 32rpx;
  background: #f3f4f6;
  border: 2rpx solid transparent;
  transition: all 0.15s ease;
}

.tag--active {
  background: #ebf0ff;
  border-color: #5b8def;
}

.tag-text {
  font-size: 26rpx;
  color: #555555;
}

.tag--active .tag-text {
  color: #5b8def;
  font-weight: 500;
}

.tag--add {
  padding: 10rpx 24rpx;
  background: #ffffff;
  border: 2rpx dashed #cbd5e1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tag-add-icon {
  font-size: 30rpx;
  color: #9ca3af;
  line-height: 1;
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

.btn--small {
  height: 72rpx;
  border-radius: 12rpx;
  flex: 1;
}

.btn-text {
  font-size: 28rpx;
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

/* ========== 头像全屏预览 ========== */
.fullscreen-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.85);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.fullscreen-avatar {
  width: 360rpx;
  height: 360rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 80rpx rgba(255, 255, 255, 0.25);
}

.fullscreen-avatar-emoji {
  font-size: 180rpx;
  line-height: 1;
}

.fullscreen-switch {
  margin-top: 80rpx;
  padding: 20rpx 64rpx;
  border-radius: 40rpx;
  background: #ffffff;
}

.fullscreen-switch-text {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2937;
}

/* ========== 底部弹层 ========== */
.picker-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: flex-end;
  z-index: 1001;
}

.picker-panel {
  width: 100%;
  background: #ffffff;
  border-radius: 32rpx 32rpx 0 0;
  padding: 40rpx 40rpx calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.picker-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 32rpx;
  text-align: center;
}

.avatar-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
  justify-content: flex-start;
}

.avatar-grid-item {
  padding: 8rpx;
  border-radius: 24rpx;
  border: 4rpx solid transparent;
}

.avatar-grid-item--active {
  border-color: #3b82f6;
  background: #eff6ff;
}

.avatar-grid-circle {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-grid-emoji {
  font-size: 52rpx;
  line-height: 1;
}

/* ========== 自定义标签弹层 ========== */
.picker-panel--custom {
  padding-bottom: calc(40rpx + env(safe-area-inset-bottom));
}

.custom-tag-input {
  height: 80rpx;
  padding: 0 24rpx;
  font-size: 28rpx;
  color: #1f2937;
  background: #f9fafb;
  border: 1rpx solid #e5e7eb;
  border-radius: 12rpx;
  margin-bottom: 28rpx;
}

.custom-tag-actions {
  display: flex;
  gap: 20rpx;
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

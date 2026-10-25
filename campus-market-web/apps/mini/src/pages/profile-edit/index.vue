<script setup lang="ts">
/**
 * 编辑资料（notes/20 D3：昵称 + 头像，局部更新）
 *
 * <p>头像走两步票据制：选图 → 上传拿 accessUrl → 随保存提交。已上传但未保存
 * 的图片会成为孤儿对象（孤儿巡检是既有遗留），可接受——用户大概率会保存。
 *
 * <p>保存成功后刷新本地会话（昵称 / 头像），返回「我的」页即换新；
 * 服务端同时失效公开资料缓存，商品列表与详情里的卖家昵称同步换新。
 */

import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { userAuthApi } from '@/api/user'
import { getSession, saveSession } from '@/utils/session'
import { uploadImage } from '@/utils/upload'

const NICK_MAX = 64

const nick = ref('')
const avatar = ref('')
const uploading = ref(false)
const saving = ref(false)

onLoad(() => {
  const s = getSession()
  nick.value = s.nickname
  avatar.value = s.avatarUrl
})

function chooseAvatar(): void {
  uni.chooseMedia({
    count: 1,
    mediaType: ['image'],
    success: (res) => {
      // res.tempFiles 的类型在 @dcloudio/types 里较松，这里显式取路径与大小
      const files = (res as unknown as { tempFiles: { tempFilePath: string; size: number }[] }).tempFiles
      const f = files[0]
      if (!f) return
      uploading.value = true
      uploadImage(f.tempFilePath, f.size)
        .then((url) => {
          avatar.value = url
        })
        .finally(() => {
          uploading.value = false
        })
    }
  })
}

async function save(): Promise<void> {
  const trimmed = nick.value.trim()
  if (!trimmed) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' })
    return
  }
  if (trimmed.length > NICK_MAX) {
    uni.showToast({ title: `昵称不超过 ${NICK_MAX} 个字符`, icon: 'none' })
    return
  }
  if (uploading.value) {
    uni.showToast({ title: '头像上传中，稍候', icon: 'none' })
    return
  }

  // 局部更新：头像没选过就不传，传空串会被服务端判非法（4021）
  const req: { nickname: string; avatarUrl?: string } = { nickname: trimmed }
  if (avatar.value) {
    req.avatarUrl = avatar.value
  }

  saving.value = true
  try {
    const vo = await userAuthApi.updateProfile(req)
    saveSession({ nickname: vo.nickname ?? '', avatarUrl: vo.avatarUrl ?? '' })
    uni.showToast({ title: '已保存', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 500)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="sec">
      <text class="sec__t">头像</text>
      <view class="avatar" hover-class="avatar--active" @click="chooseAvatar">
        <image v-if="avatar" class="avatar__img" :src="avatar" mode="aspectFill" />
        <text v-else class="avatar__plus">＋</text>
        <view v-if="uploading" class="avatar__loading">
          <text class="avatar__loading-t">上传中</text>
        </view>
      </view>
      <text class="sec__hint">点击更换，支持 jpg / png / webp，不超过 5MB</text>
    </view>

    <view class="sec">
      <text class="sec__t">昵称</text>
      <input
        v-model="nick"
        class="input"
        type="text"
        :maxlength="NICK_MAX"
        placeholder="请输入昵称"
        placeholder-class="input__ph"
      />
      <text class="sec__hint">昵称展示在商品列表、详情与留言中，暂不支持敏感词过滤</text>
    </view>

    <view class="submit" hover-class="submit--active" :class="{ 'submit--off': saving || uploading }" @click="save">
      <text class="submit__t">{{ saving ? '保存中' : '保存' }}</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: $cm-space-4 $cm-space-4 180rpx;
}

.sec {
  margin-bottom: $cm-space-4;
  padding: $cm-space-5;
  background: $cm-color-surface;
  border-radius: $cm-radius-xl;
}

.sec__t {
  display: block;
  margin-bottom: $cm-space-4;
  font-size: $cm-font-size-base;
  font-weight: 700;
  color: $cm-color-text-1;
}

.sec__hint {
  display: block;
  margin-top: $cm-space-3;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.avatar {
  position: relative;
  width: 168rpx;
  height: 168rpx;
  border-radius: 50%;
  overflow: hidden;
  background: $cm-color-bg;
  border: 1rpx dashed $cm-color-border-strong;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 120ms ease-out;
}

.avatar--active {
  transform: scale(0.96);
}

.avatar__img {
  width: 100%;
  height: 100%;
}

.avatar__plus {
  font-size: 64rpx;
  color: $cm-color-text-3;
}

.avatar__loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}

.avatar__loading-t {
  font-size: $cm-font-size-xs;
  color: #ffffff;
}

.input {
  height: 72rpx;
  padding: 0 $cm-space-3;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.input__ph {
  color: $cm-color-text-3;
}

.submit {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: $cm-color-brand;
  border-radius: $cm-radius-md;
  transition: transform 120ms ease-out;
}

.submit--active {
  transform: scale(0.98);
}

.submit--off {
  opacity: 0.5;
}

.submit__t {
  font-size: $cm-font-size-base;
  font-weight: 600;
  color: #ffffff;
}
</style>

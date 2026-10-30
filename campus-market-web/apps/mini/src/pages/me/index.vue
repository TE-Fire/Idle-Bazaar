<script setup lang="ts">
/**
 * M6 我的（design/02 §六，P0）
 *
 * <p>「我发布的 / 我买到的 / 我卖出的」三项入口合并到「我的交易」页（顶部三 tab，
 * 「我发布的」默认不出已售、已售以订单形态归到「我卖出的」，避免同商品两边重复）。
 * 收藏与学号认证都已有落点页面（notes/24、notes/20 D5）。
 * 未登录时点头像区进登录页；已登录进资料编辑页（昵称 / 头像，notes/20 D3）。
 *
 * <p>2026-10-28 视觉改版：头部从「白底一行」换成品牌渐变身份卡（导航栏同步改成
 * 同色，避免出现一条白带把它切断）；订单与服务各自成卡、每行带语义色图标块，
 * 不再是一列只有文字和箭头的白条。
 */

import { onShow } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { getSession, clearSession } from '@/utils/session'
import { userAuthApi } from '@/api/user'
import CmIcon from '@/components/CmIcon.vue'
import TabBar from '@/components/TabBar.vue'

const nick = ref('')
const avatar = ref('')

interface OrderEntry {
  key: string
  label: string
  icon: string
  tint: string
}
interface ServiceEntry {
  key: string
  label: string
  icon: string
  tint: string
}

/** 订单快览：三栏平铺，点进对应列表（闲鱼式紧凑布局） */
const orderEntries: OrderEntry[] = [
  { key: 'published', label: '我发布的', icon: 'box', tint: 'brand' },
  { key: 'bought', label: '我买到的', icon: 'receipt', tint: 'violet' },
  { key: 'sold', label: '我卖出的', icon: 'check', tint: 'teal' }
]

/** 我的服务：分组平铺（关注 / 粉丝收进头部，不单列） */
const serviceEntries: ServiceEntry[] = [
  { key: 'moments', label: '我的动态', icon: 'chat', tint: 'brand' },
  { key: 'plaza', label: '逛广场', icon: 'grid', tint: 'violet' },
  { key: 'notice', label: '系统通知', icon: 'bell', tint: 'blue' },
  { key: 'fav', label: '我的收藏', icon: 'heart', tint: 'pink' },
  { key: 'verify', label: '学号认证', icon: 'shield', tint: 'teal' }
]

/** onShow：从登录页 / 编辑页返回时要刷新昵称与头像 */
onShow(() => {
  const s = getSession()
  nick.value = s.nickname
  avatar.value = s.avatarUrl
})

function open(e: { key: string; label: string }): void {
  if (e.key === 'published') {
    void uni.navigateTo({ url: '/pages/transactions/index?tab=0' })
    return
  }
  if (e.key === 'bought') {
    void uni.navigateTo({ url: '/pages/transactions/index?tab=1' })
    return
  }
  if (e.key === 'sold') {
    void uni.navigateTo({ url: '/pages/transactions/index?tab=2' })
    return
  }
  if (e.key === 'moments') {
    // 自己的主页 → 动态 Tab；未登录先登录，否则主页会提示「用户不存在」
    const me = getSession().userId
    if (!me) {
      goLogin()
      return
    }
    void uni.navigateTo({ url: `/pages/user/index?userId=${me}&tab=moments` })
    return
  }
  if (e.key === 'plaza') {
    void uni.navigateTo({ url: '/pages/moments/index' })
    return
  }
  if (e.key === 'notice') {
    void uni.navigateTo({ url: '/pages/notice/index' })
    return
  }
  if (e.key === 'fav') {
    void uni.navigateTo({ url: '/pages/favorites/index' })
    return
  }
  if (e.key === 'verify') {
    if (!getSession().tokenValue) {
      goLogin()
      return
    }
    void uni.navigateTo({ url: '/pages/verify/index' })
    return
  }
  uni.showToast({ title: `「${e.label}」待建`, icon: 'none' })
}

/** 头像区：未登录进登录页，已登录进资料编辑（notes/20 D3/D4） */
function onHead(): void {
  if (!nick.value) {
    goLogin()
    return
  }
  void uni.navigateTo({ url: '/pages/profile-edit/index' })
}

function goLogin(): void {
  void uni.navigateTo({ url: '/pages/login/index' })
}

/** 关注 / 粉丝：收进头部，点开关注列表（默认落在关注 tab） */
function goFollows(): void {
  void uni.navigateTo({ url: '/pages/follows/index' })
}

async function logout(): Promise<void> {
  try {
    await userAuthApi.logout()
  } finally {
    clearSession()
    nick.value = ''
    uni.showToast({ title: '已退出', icon: 'none' })
  }
}
</script>

<template>
  <view class="cm-page cm-page--tab">
    <!-- ① 渐变身份卡：整块深色底上放白色文字，是全 app 辨识度最高的一块 -->
    <view class="idcard">
      <view class="idcard__glow" />
      <view class="idcard__glow idcard__glow--b" />

      <view class="idcard__row" hover-class="idcard__row--on" @click="onHead">
        <view class="idcard__avatar">
          <image v-if="avatar" class="idcard__img" :src="avatar" mode="aspectFill" />
          <text v-else class="idcard__ch">{{ nick ? nick.slice(0, 1) : '我' }}</text>
        </view>
        <view class="idcard__meta">
          <text class="idcard__nick">{{ nick || '点击登录' }}</text>
          <text class="idcard__sub">{{ nick ? '点击编辑资料与头像' : '登录后同步交易与动态' }}</text>
        </view>
        <CmIcon name="arrow" tone="white" size="sm" />
      </view>

      <view v-if="nick" class="idcard__foot">
        <view class="idcard__chip" hover-class="idcard__chip--on" @click.stop="goFollows">
          <text>关注与粉丝</text>
          <CmIcon name="arrow" tone="white" size="sm" />
        </view>
      </view>
    </view>

    <!-- ② 订单快览：三栏等分，图标块在上、文字在下 -->
    <view class="orders cm-card cm-in" style="animation-delay: 60ms">
      <view
        v-for="e in orderEntries"
        :key="e.key"
        class="orders__item"
        hover-class="orders__item--on"
        @click="open(e)"
      >
        <view class="tile" :class="'tile--' + e.tint">
          <CmIcon :name="e.icon" size="sm" />
        </view>
        <text class="orders__label">{{ e.label }}</text>
      </view>
    </view>

    <!-- ③ 我的服务 -->
    <view class="cm-sect">
      <view class="cm-sect__bar" />
      <text class="cm-sect__t">我的服务</text>
    </view>

    <view class="svcs cm-card cm-in" style="animation-delay: 120ms">
      <view
        v-for="e in serviceEntries"
        :key="e.key"
        class="svc"
        hover-class="svc--on"
        @click="open(e)"
      >
        <view class="tile" :class="'tile--' + e.tint">
          <CmIcon :name="e.icon" size="sm" />
        </view>
        <text class="svc__label">{{ e.label }}</text>
        <CmIcon name="arrow" tone="muted" size="sm" class="svc__arrow" />
      </view>
    </view>

    <view v-if="nick" class="logout" hover-class="logout--on" @click="logout">
      <text>退出登录</text>
    </view>

    <TabBar active="me" />
  </view>
</template>

<style scoped lang="scss">
/* 顶部留一点呼吸：身份卡不要贴着导航栏 */
.cm-page {
  padding-top: $cm-space-4;
}

/* ── ① 身份卡 ───────────────────────────────────────────── */
.idcard {
  position: relative;
  margin: 0 $cm-space-4;
  padding: $cm-space-5 $cm-space-4 $cm-space-4;
  border-radius: $cm-radius-3xl;
  background-image: linear-gradient(135deg, #FF9248 0%, $cm-color-brand 52%, $cm-color-brand-2 100%);
  box-shadow: 0 16rpx 36rpx -14rpx rgba(255, 106, 0, 0.6);
  overflow: hidden;
}

/* 两团白色柔光：让大色块不死板，同时给出前后层次 */
.idcard__glow {
  position: absolute;
  right: -80rpx;
  top: -100rpx;
  width: 300rpx;
  height: 300rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0));
}

.idcard__glow--b {
  right: auto;
  left: -60rpx;
  top: auto;
  bottom: -120rpx;
  width: 240rpx;
  height: 240rpx;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.18), rgba(255, 255, 255, 0));
}

.idcard__row {
  position: relative;
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  transition: transform 140ms ease-out;
}

.idcard__row--on {
  transform: scale(0.99);
}

.idcard__avatar {
  width: 124rpx;
  height: 124rpx;
  border-radius: 50%;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.24);
  /* 白色描边代替阴影：在彩色底上更干净 */
  border: 4rpx solid rgba(255, 255, 255, 0.85);
}

.idcard__img {
  width: 100%;
  height: 100%;
}

.idcard__ch {
  font-size: 48rpx;
  font-weight: $cm-font-weight-heavy;
  color: #fff;
}

.idcard__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.idcard__nick {
  font-size: 38rpx;
  font-weight: $cm-font-weight-heavy;
  color: #fff;
  letter-spacing: 0.6rpx;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.idcard__sub {
  font-size: $cm-font-size-xs;
  color: rgba(255, 255, 255, 0.82);
}

.idcard__foot {
  position: relative;
  margin-top: $cm-space-4;
  display: flex;
}

/* 玻璃胶囊：半透明白底 + 白描边，不抢主信息 */
.idcard__chip {
  display: flex;
  align-items: center;
  gap: 2rpx;
  height: 56rpx;
  padding: 0 $cm-space-2 0 $cm-space-3;
  border-radius: $cm-radius-pill;
  font-size: $cm-font-size-xs;
  color: #fff;
  background: rgba(255, 255, 255, 0.2);
  border: 1rpx solid rgba(255, 255, 255, 0.45);
  transition: background 160ms ease-out, transform 140ms ease-out;
}

.idcard__chip--on {
  background: rgba(255, 255, 255, 0.34);
  transform: scale(0.96);
}

/* ── 图标块：统一形状、语义配色（tile--* 是唯一的色差来源） ── */
.tile {
  width: 76rpx;
  height: 76rpx;
  border-radius: $cm-radius-xl;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.tile--brand {
  background: $cm-color-brand-bg;
  color: $cm-color-brand;
}

.tile--violet {
  background: #F0EBFF;
  color: $cm-color-accent-violet;
}

.tile--teal {
  background: #E4F8F5;
  color: $cm-color-accent-teal;
}

.tile--blue {
  background: #E9F2FF;
  color: $cm-color-blue-500;
}

.tile--pink {
  background: #FFEBF0;
  color: $cm-color-brand-2;
}

/* ── ② 订单快览 ─────────────────────────────────────────── */
.orders {
  margin: $cm-space-3 $cm-space-4 0;
  padding: $cm-space-4 $cm-space-2;
  display: flex;
}

.orders__item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  transition: transform 140ms ease-out;
}

.orders__item--on {
  transform: scale(0.94);
}

.orders__label {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
}

/* ── ③ 我的服务 ─────────────────────────────────────────── */
.svcs {
  margin: 0 $cm-space-4;
  padding: 0 $cm-space-4;
}

.svc {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
  padding: $cm-space-3 0;
  border-top: 1rpx solid $cm-color-border;
  transition: background 140ms ease-out;
}

.svc:first-child {
  border-top: none;
}

.svc--on {
  background: $cm-color-bg;
}

.svc__label {
  flex: 1;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
}

.svc__arrow {
  opacity: 0.5;
}

/* ── 退出 ───────────────────────────────────────────────── */
.logout {
  margin: $cm-space-5 $cm-space-4 0;
  height: 92rpx;
  border-radius: $cm-radius-pill;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $cm-font-size-base;
  color: $cm-color-err;
  background: $cm-color-surface;
  border: 1rpx solid $cm-color-err-bg;
  transition: transform 140ms ease-out, background 140ms ease-out;
}

.logout--on {
  transform: scale(0.98);
  background: $cm-color-err-bg;
}
</style>

<script setup lang="ts">
/**
 * 底部导航（自绘，替代原生 tabBar）
 *
 * <p>为什么自绘：原生 tabBar 只能配颜色和图片，选中态无法动画、中央无法做上浮的
 * 发布按钮，也无法圆角悬浮——这三件正是「切换生硬」的根源。自绘后用 reLaunch 切换，
 * 每页各自跑一次入场动画，切 tab 就有了统一的节奏。
 *
 * <p>代价与对策：reLaunch 会清空页面栈（与 switchTab 语义一致），且需要每个 tab 页
 * 自己引入本组件、自己留出底部高度（`.cm-page--tab`）。跳转统一走 `goTab`，
 * 不要在各页面直接写 `uni.reLaunch`。
 */

import { onMounted, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { userChatApi } from '@/api/user'
import { getSession } from '@/utils/session'
import { goTab, type TabKey } from '@/utils/tab-nav'
import CmIcon from './CmIcon.vue'

const props = defineProps<{ active: TabKey }>()

interface TabItem {
  key: TabKey
  label: string
  icon: string
}

const ITEMS: TabItem[] = [
  { key: 'home', label: '首页', icon: 'home' },
  { key: 'category', label: '分类', icon: 'grid' },
  { key: 'publish', label: '发布', icon: 'plus' },
  { key: 'chat', label: '消息', icon: 'chat' },
  { key: 'me', label: '我的', icon: 'me' }
]

/** 选中光块的位移：5 格等分，用 translateX(百分比) 走 GPU，不触发布局 */
const activeIndex = () => Math.max(0, ITEMS.findIndex((t) => t.key === props.active))

/** 消息未读总数：登录态才拉，失败静默（导航栏不该被网络问题拖住） */
const unread = ref(0)

async function refreshUnread(): Promise<void> {
  if (!getSession().userId) {
    unread.value = 0
    return
  }
  try {
    const list = await userChatApi.conversations()
    unread.value = list.reduce((sum, c) => sum + (c.unread ?? 0), 0)
  } catch {
    unread.value = 0
  }
}

onMounted(refreshUnread)
onShow(refreshUnread)

function onTap(t: TabItem): void {
  if (t.key === props.active) return
  goTab(t.key)
}
</script>

<template>
  <view class="tabbar">
    <!-- 滑动光块：跟着选中项平移，中央「发布」不参与 -->
    <view
      class="tabbar__glow"
      :class="{ 'tabbar__glow--off': active === 'publish' }"
      :style="{ transform: `translateX(${activeIndex() * 100}%)` }"
    >
      <view class="tabbar__pill" />
    </view>

    <view
      v-for="t in ITEMS"
      :key="t.key"
      class="tab"
      :class="[`tab--${t.key}`, { 'tab--on': active === t.key }]"
      hover-class="tab--press"
      :hover-stay-time="80"
      @click="onTap(t)"
    >
      <!-- 中央发布：上浮的渐变圆钮，选中时十字转 45° 变成「关闭」语义 -->
      <template v-if="t.key === 'publish'">
        <view class="pub" :class="{ 'pub--on': active === 'publish' }" hover-class="pub--press">
          <CmIcon name="plus" tone="white" size="lg" />
        </view>
      </template>

      <template v-else>
        <view class="tab__icon">
          <CmIcon :name="t.icon" :tone="active === t.key ? 'brand' : 'muted'" />
          <text v-if="t.key === 'chat' && unread" class="tab__badge">{{ unread > 99 ? '99+' : unread }}</text>
        </view>
        <text class="tab__label">{{ t.label }}</text>
      </template>
    </view>
  </view>
</template>

<style scoped lang="scss">
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 90;
  height: calc(#{$cm-layout-tabbar-h} + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  display: flex;
  align-items: flex-start;
  background: $cm-color-surface;
  border-radius: $cm-radius-3xl $cm-radius-3xl 0 0;
  box-shadow: $cm-shadow-dock;
}

/* ── 选中光块 ── */
.tabbar__glow {
  position: absolute;
  left: 0;
  top: 0;
  width: 20%;
  height: 100%;
  display: flex;
  justify-content: center;
  padding-top: 8rpx;
  /* 320ms + 自定义曲线：比导航栏其余反馈稍慢，读起来是「滑过去」而不是「跳过去」 */
  transition: transform 320ms cubic-bezier(0.22, 1, 0.36, 1), opacity 180ms ease-out;
  pointer-events: none;
}

.tabbar__glow--off {
  opacity: 0;
}

.tabbar__pill {
  width: 84rpx;
  height: 56rpx;
  border-radius: $cm-radius-pill;
  background-image: linear-gradient(135deg, $cm-color-brand-bg, #FFE2CC);
}

/* ── 单项 ── */
.tab {
  flex: 1;
  height: $cm-layout-tabbar-h;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
  transition: transform 140ms ease-out;
}

.tab--press .tab__icon {
  transform: scale(0.86);
}

.tab__icon {
  position: relative;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 140ms ease-out;
}

.tab__label {
  font-size: 20rpx;
  color: $cm-color-text-3;
  letter-spacing: 0.4rpx;
  transition: color 180ms ease-out;
}

/* 选中：字重与颜色一起变，光块已经在后面了，不再加粗太多 */
.tab--on .tab__label {
  color: $cm-color-brand;
  font-weight: $cm-font-weight-bold;
}

.tab__badge {
  position: absolute;
  top: -6rpx;
  right: -14rpx;
  min-width: 26rpx;
  height: 26rpx;
  padding: 0 6rpx;
  border-radius: $cm-radius-pill;
  background-image: linear-gradient(135deg, #FF6B6B, $cm-color-brand-2);
  border: 2rpx solid $cm-color-surface;
  color: #fff;
  font-size: 18rpx;
  line-height: 26rpx;
  text-align: center;
}

/* ── 中央发布 ── */
.tab--publish {
  position: relative;
}

.pub {
  width: 98rpx;
  height: 98rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 上浮到导航栏之上，形成「中间被顶起来」的层次 */
  transform: translateY(-26rpx);
  background-image: linear-gradient(135deg, $cm-color-brand-glow 0%, $cm-color-brand 60%, $cm-color-brand-dark 100%);
  box-shadow: $cm-shadow-brand;
  border: 6rpx solid $cm-color-surface;
  transition: transform 220ms cubic-bezier(0.34, 1.56, 0.64, 1);
}

.pub--press {
  transform: translateY(-26rpx) scale(0.92);
}

/* 已在发布页：十字转 45° 并回落，表示「收起」 */
.pub--on {
  transform: translateY(-18rpx) rotate(45deg);
  box-shadow: 0 6rpx 14rpx rgba(255, 106, 0, 0.22);
}
</style>

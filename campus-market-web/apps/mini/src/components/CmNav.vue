<script setup lang="ts">
/**
 * 自定义导航栏（接管原生导航栏）
 *
 * <p>全局 navigationStyle=custom 后，状态栏留白、标题、返回都由这里负责。
 * 固定定位 + 等高占位：长列表滚动时返回入口不跟着划走。
 * tab 根页（自带大头部、无需返回）用 variant="pad"，只输出状态栏高度的占位。
 */
import CmIcon from '@/components/CmIcon.vue'

const props = withDefaults(
  defineProps<{
    title?: string
    /** bar 白底细栏 / pad 仅状态栏占位 */
    variant?: 'bar' | 'pad'
    /** 是否给返回入口；tab 根页传 false */
    back?: boolean
  }>(),
  { title: '', variant: 'bar', back: true }
)

const statusBarHeight = uni.getSystemInfoSync().statusBarHeight ?? 0

/** 占位高度：状态栏 + 栏高。88rpx 与 $cm-layout-nav-h 对齐 */
const ghostHeight =
  props.variant === 'pad' ? `${statusBarHeight}px` : `calc(${statusBarHeight}px + 88rpx)`

/** 没有上一页（分享/直接落地）时回首页，不留死路 */
function goBack(): void {
  if (getCurrentPages().length > 1) {
    void uni.navigateBack()
  } else {
    void uni.reLaunch({ url: '/pages/index/index' })
  }
}
</script>

<template>
  <view class="nav">
    <view v-if="variant === 'bar'" class="nav__bar" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="nav__row">
        <view v-if="back" class="nav__back" hover-class="nav__back--on" @click="goBack">
          <CmIcon name="back" tone="ink" size="sm" />
        </view>
        <view v-else class="nav__side" />
        <text class="nav__t">{{ title }}</text>
        <view class="nav__side">
          <slot name="right" />
        </view>
      </view>
    </view>
    <view class="nav__ghost" :style="{ height: ghostHeight }" />
  </view>
</template>

<style scoped lang="scss">
/* 四边逐个写：uni 的 rpx 转换链下 inset 不可靠 */
.nav__bar {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  z-index: 80;
  background: $cm-color-surface;
  box-shadow: 0 2rpx 10rpx rgba(27, 27, 33, 0.04);
}

.nav__row {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: $cm-layout-nav-h;
  padding: 0 $cm-space-3;
  box-sizing: border-box;
}

/* 标题绝对居中：左右两侧宽度不等时也不会偏 */
.nav__t {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  max-width: 400rpx;
  font-size: $cm-font-size-md;
  font-weight: $cm-font-weight-bold;
  color: $cm-color-text-1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav__back,
.nav__side {
  position: relative;
  z-index: 1;
  width: 64rpx;
  height: 64rpx;
  flex: none;
}

.nav__back {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $cm-radius-pill;
  background: $cm-color-bg;
}

.nav__back--on {
  background: $cm-color-border;
}

.nav__side {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
</style>

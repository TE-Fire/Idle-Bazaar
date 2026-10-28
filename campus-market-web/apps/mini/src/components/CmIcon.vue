<script setup lang="ts">
/**
 * 统一图标（纯 CSS 绘制，零图片资源）
 *
 * <p>为什么不用字体图标 / SVG：小程序 `image` 不支持 SVG，字体文件要额外体积且
 * 微信端 @font-face 不稳；这套图标全部用 border / background / transform 画，
 * H5 与 mp-weixin 表现一致，换色只改 `currentColor`，不需要多套资源。
 *
 * <p>约定：所有图标画在 44×44 的方框里，线宽 3rpx，圆角统一 6–10rpx。
 * 尺寸只有三档（sm 36 / md 44 / lg 52），避免各页面自定尺寸导致视觉不齐。
 */

withDefaults(
  defineProps<{
    /** 图标名：home / grid / plus / chat / me / bell / heart / search / arrow / clock / box / location / check / shield / receipt */
    name: string
    /** 语义色：ink 主文字 / muted 弱化 / brand 品牌 / white 反白 / none 继承父级 */
    tone?: 'ink' | 'muted' | 'brand' | 'white' | 'none'
    size?: 'sm' | 'md' | 'lg'
  }>(),
  { tone: 'none', size: 'md' }
)
</script>

<template>
  <view class="i" :class="[`i--${name}`, `i--tone-${tone}`, `i--${size}`]">
    <view class="i__b" />
  </view>
</template>

<style scoped lang="scss">
/* ── 基础：颜色一律走 currentColor，容器改 color 即可换色 ── */
.i {
  position: relative;
  width: 44rpx;
  height: 44rpx;
  flex: none;
  color: $cm-color-text-2;
}

.i--sm {
  transform: scale(0.82);
}

.i--lg {
  transform: scale(1.16);
}

.i--tone-ink {
  color: $cm-color-text-1;
}

.i--tone-muted {
  color: $cm-color-text-3;
}

.i--tone-brand {
  color: $cm-color-brand;
}

.i--tone-white {
  color: #fff;
}

/* ── home：屋顶折线 + 屋身 ── */
.i--home::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 7rpx;
  width: 19rpx;
  height: 19rpx;
  border-top: 3rpx solid currentColor;
  border-left: 3rpx solid currentColor;
  border-radius: 4rpx 0 0 0;
  transform: translateX(-32%) rotate(45deg);
}

.i--home::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 7rpx;
  width: 28rpx;
  height: 19rpx;
  border: 3rpx solid currentColor;
  border-top: none;
  border-radius: 0 0 6rpx 6rpx;
  transform: translateX(-50%);
}

/* ── grid：2×2 实心方块（分类） ── */
.i--grid::before {
  content: '';
  position: absolute;
  left: 7rpx;
  top: 7rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 3rpx;
  background: currentColor;
  box-shadow: 18rpx 0 0 currentColor, 0 18rpx 0 currentColor, 18rpx 18rpx 0 currentColor;
}

/* ── plus：两根圆头短棒 ── */
.i--plus::before,
.i--plus::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  background: currentColor;
  border-radius: 3rpx;
}

.i--plus::before {
  width: 26rpx;
  height: 4rpx;
  transform: translate(-50%, -50%);
}

.i--plus::after {
  width: 4rpx;
  height: 26rpx;
  transform: translate(-50%, -50%);
}

/* ── chat：圆角气泡 + 左下小尾 ── */
.i--chat::before {
  content: '';
  position: absolute;
  left: 5rpx;
  top: 8rpx;
  width: 34rpx;
  height: 24rpx;
  border: 3rpx solid currentColor;
  border-radius: 10rpx;
}

.i--chat::after {
  content: '';
  position: absolute;
  left: 12rpx;
  bottom: 7rpx;
  width: 9rpx;
  height: 9rpx;
  border-left: 3rpx solid currentColor;
  border-bottom: 3rpx solid currentColor;
  border-radius: 0 0 0 3rpx;
  transform: rotate(-45deg);
}

/* ── me：头 + 肩弧 ── */
.i--me::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 6rpx;
  width: 14rpx;
  height: 14rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
  transform: translateX(-50%);
}

.i--me::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 7rpx;
  width: 28rpx;
  height: 15rpx;
  border: 3rpx solid currentColor;
  border-bottom: none;
  border-radius: 15rpx 15rpx 0 0;
  transform: translateX(-50%);
}

/* ── bell：钟罩 + 底口 ── */
.i--bell::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 7rpx;
  width: 27rpx;
  height: 23rpx;
  border: 3rpx solid currentColor;
  border-bottom: none;
  border-radius: 14rpx 14rpx 5rpx 5rpx;
  transform: translateX(-50%);
}

.i--bell::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 9rpx;
  width: 28rpx;
  height: 4rpx;
  border-radius: 2rpx;
  background: currentColor;
  transform: translateX(-50%);
}

/* ── heart：两个圆 + 45° 方块拼合 ── */
.i--heart .i__b {
  position: absolute;
  left: 11rpx;
  top: 14rpx;
  width: 22rpx;
  height: 22rpx;
  background: currentColor;
  border-radius: 4rpx;
  transform: rotate(45deg);
}

.i--heart::before,
.i--heart::after {
  content: '';
  position: absolute;
  top: 10rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: currentColor;
}

.i--heart::before {
  left: 6rpx;
}

.i--heart::after {
  left: 22rpx;
}

/* ── search：圆 + 斜柄 ── */
.i--search::before {
  content: '';
  position: absolute;
  left: 7rpx;
  top: 7rpx;
  width: 22rpx;
  height: 22rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
}

.i--search::after {
  content: '';
  position: absolute;
  right: 8rpx;
  bottom: 10rpx;
  width: 12rpx;
  height: 3rpx;
  background: currentColor;
  border-radius: 2rpx;
  transform: rotate(45deg);
}

/* ── arrow：右向折线 ── */
.i--arrow::before {
  content: '';
  position: absolute;
  left: 14rpx;
  top: 13rpx;
  width: 14rpx;
  height: 14rpx;
  border-top: 3rpx solid currentColor;
  border-right: 3rpx solid currentColor;
  border-radius: 2rpx;
  transform: rotate(45deg);
}

/* ── clock：表盘 + 指针 ── */
.i--clock::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 28rpx;
  height: 28rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.i--clock::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 3rpx;
  height: 10rpx;
  background: currentColor;
  border-radius: 2rpx;
  transform: translate(-50%, -100%);
}

/* ── box：包裹 + 封条 ── */
.i--box::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 9rpx;
  width: 30rpx;
  height: 24rpx;
  border: 3rpx solid currentColor;
  border-radius: 6rpx;
  transform: translateX(-50%);
}

.i--box::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 9rpx;
  width: 3rpx;
  height: 9rpx;
  background: currentColor;
  transform: translateX(-50%);
}

/* ── location：水滴定位 ── */
.i--location::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 6rpx;
  width: 23rpx;
  height: 23rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
  transform: translateX(-50%);
}

.i--location::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 25rpx;
  width: 0;
  height: 0;
  border-left: 9rpx solid transparent;
  border-right: 9rpx solid transparent;
  border-top: 12rpx solid currentColor;
  transform: translateX(-50%);
}

/* ── check：勾 ── */
.i--check::before {
  content: '';
  position: absolute;
  left: 14rpx;
  top: 12rpx;
  width: 16rpx;
  height: 9rpx;
  border-left: 4rpx solid currentColor;
  border-bottom: 4rpx solid currentColor;
  border-radius: 2rpx;
  transform: rotate(-45deg);
}

/* ── shield：盾牌 + 内勾（学号认证） ── */
.i--shield::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 6rpx;
  width: 28rpx;
  height: 30rpx;
  border: 3rpx solid currentColor;
  border-radius: 6rpx 6rpx 14rpx 14rpx / 6rpx 6rpx 22rpx 22rpx;
  transform: translateX(-50%);
}

.i--shield::after {
  content: '';
  position: absolute;
  left: 18rpx;
  top: 18rpx;
  width: 9rpx;
  height: 5rpx;
  border-left: 3rpx solid currentColor;
  border-bottom: 3rpx solid currentColor;
  transform: rotate(-45deg);
}

/* ── receipt：单据 + 两行明细（交易） ── */
.i--receipt::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 7rpx;
  width: 24rpx;
  height: 30rpx;
  border: 3rpx solid currentColor;
  border-radius: 5rpx;
  transform: translateX(-50%);
}

.i--receipt::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 15rpx;
  width: 12rpx;
  height: 3rpx;
  background: currentColor;
  border-radius: 2rpx;
  transform: translateX(-50%);
  box-shadow: 0 8rpx 0 currentColor;
}
</style>

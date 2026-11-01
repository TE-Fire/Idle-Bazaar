<script setup lang="ts">
/**
 * 底部半屏面板：议价 / 拒绝理由共用。
 *
 * <p>用 v-if 而不是 v-show——关闭后不留 DOM，避免小程序里键盘收起后残留输入框焦点。
 * 遮罩接管 touchmove，防止面板外的手指滚动带动页面。
 */

const props = defineProps<{
  modelValue: boolean
  title: string
}>()

const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

function close(): void {
  emit('update:modelValue', false)
}
</script>

<template>
  <view v-if="props.modelValue" class="sheet">
    <view class="sheet__mask" @click="close" @touchmove.stop.prevent />
    <view class="sheet__panel">
      <view class="sheet__head">
        <text class="sheet__title">{{ title }}</text>
        <text class="sheet__close" @click="close">×</text>
      </view>
      <slot />
    </view>
  </view>
</template>

<style scoped lang="scss">
/* 不靠 flex 定位：面板用绝对定位钉在容器底边。
   uni-app 的 h5 里 flex + align-items:flex-end 在小程序组件树上表现不一致，
   显式 bottom 更可靠 */
.sheet {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 200;
}

/* 入场动画跟着「允许动效」走：系统开了减弱动效时直接出现，不做位移 */
@media (prefers-reduced-motion: no-preference) {
  .sheet__mask {
    animation: mask-in 200ms ease-out;
  }

  .sheet__panel {
    animation: sheet-in 260ms cubic-bezier(0.22, 1, 0.36, 1);
  }
}

.sheet__mask {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(20, 18, 14, 0.45);
}

.sheet__panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  /* 小屏兜底：内容超高时面板自己滚，不把按钮顶出屏幕 */
  max-height: 86vh;
  overflow-y: auto;
  padding: $cm-space-4 $cm-space-4 calc(#{$cm-space-4} + env(safe-area-inset-bottom));
  background: $cm-color-surface;
  border-radius: $cm-radius-3xl $cm-radius-3xl 0 0;
  box-shadow: $cm-shadow-dock;
}

.sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $cm-space-3;
}

.sheet__title {
  font-size: $cm-font-size-lg;
  font-weight: 600;
  color: $cm-color-text-1;
}

.sheet__close {
  padding: 0 $cm-space-1;
  font-size: 40rpx;
  line-height: 1;
  color: $cm-color-text-3;
}

@keyframes mask-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes sheet-in {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}
</style>

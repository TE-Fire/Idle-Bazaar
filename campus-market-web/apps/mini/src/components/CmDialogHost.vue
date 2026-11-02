<script setup lang="ts">
/**
 * 弹窗宿主：把 {@link dialogState} 里的请求渲染成主题化卡片。
 *
 * <p>每页挂一个即可（会用到弹窗的页面才需要）。样式与 OfferSheet 同源：
 * 遮罩同色、圆角同档，弹窗只是「居中而非贴底」的另一种壳。
 */

import { ref, watch } from 'vue'
import { dialogState, settle } from '@/utils/dialog'

const req = dialogState
const text = ref('')
const focus = ref(false)

/** 每次换请求都清空输入并重新聚焦：否则上一单的核销码会留在下一单里 */
watch(
  () => req.current,
  (cur) => {
    text.value = ''
    focus.value = false
    if (cur?.kind !== 'prompt') return
    // 等一帧再聚焦：小程序里在同一帧内挂载并聚焦，键盘不会弹
    setTimeout(() => {
      focus.value = true
    }, 80)
  }
)

function cancel(): void {
  const cur = req.current
  if (!cur) return
  if (cur.kind === 'actions') settle(-1)
  else if (cur.kind === 'prompt') settle(null)
  else settle(false)
}

function confirm(): void {
  const cur = req.current
  if (!cur) return
  if (cur.kind === 'prompt') settle(text.value.trim())
  else settle(true)
}

function pick(i: number): void {
  settle(i)
}
</script>

<template>
  <view v-if="req.current" class="dlg">
    <view class="dlg__mask" @click="cancel" @touchmove.stop.prevent />

    <!-- 动作菜单：贴底，选项多时比居中卡片更好点 -->
    <view v-if="req.current.kind === 'actions'" class="dlg__sheet">
      <text v-if="req.current.title" class="dlg__sheet-t">{{ req.current.title }}</text>
      <view class="dlg__acts">
        <view
          v-for="(it, i) in req.current.items"
          :key="it"
          class="dlg__act"
          hover-class="dlg__act--on"
          @click="pick(i)"
        >
          <text>{{ it }}</text>
        </view>
      </view>
      <view class="dlg__act dlg__act--cancel" hover-class="dlg__act--on" @click="cancel">
        <text>取消</text>
      </view>
    </view>

    <!-- 确认 / 提示 / 输入：居中卡片 -->
    <view v-else class="dlg__card">
      <text v-if="req.current.title" class="dlg__title">{{ req.current.title }}</text>
      <text v-if="req.current.content" class="dlg__content">{{ req.current.content }}</text>

      <view v-if="req.current.lines?.length" class="dlg__lines">
        <view v-for="(l, i) in req.current.lines" :key="i" class="dlg__line">
          <text v-if="l.label" class="dlg__k">{{ l.label }}</text>
          <text class="dlg__v" :class="{ 'dlg__v--strong': l.strong }">{{ l.value }}</text>
        </view>
      </view>

      <input
        v-if="req.current.kind === 'prompt'"
        v-model="text"
        class="dlg__input"
        :focus="focus"
        :placeholder="req.current.placeholder || ''"
        :maxlength="req.current.maxlength ?? 200"
        placeholder-class="dlg__ph"
      />

      <text v-if="req.current.note" class="dlg__note">{{ req.current.note }}</text>

      <view class="dlg__ops">
        <view
          v-if="req.current.kind !== 'alert'"
          class="dlg__btn dlg__btn--ghost"
          hover-class="dlg__btn--on"
          @click="cancel"
        >
          <text>{{ req.current.cancelText || '取消' }}</text>
        </view>
        <view
          class="dlg__btn"
          :class="req.current.danger ? 'dlg__btn--danger' : 'dlg__btn--primary'"
          hover-class="dlg__btn--on"
          @click="confirm"
        >
          <text>{{ req.current.confirmText || '确定' }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.dlg {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 900;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 $cm-space-6;
}

.dlg__mask {
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  background: rgba(20, 18, 14, 0.45);
}

@media (prefers-reduced-motion: no-preference) {
  .dlg__mask {
    animation: dlgMask 180ms ease-out;
  }

  .dlg__card {
    animation: dlgPop 220ms cubic-bezier(0.22, 1, 0.36, 1);
  }

  .dlg__sheet {
    animation: dlgUp 240ms cubic-bezier(0.22, 1, 0.36, 1);
  }
}

/* 居中卡：宽度按屏幕比例走，小屏不贴边、大屏不拉长 */
.dlg__card {
  position: relative;
  width: 100%;
  max-width: 620rpx;
  max-height: 76vh;
  overflow-y: auto;
  padding: $cm-space-5 $cm-space-4 $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-3xl;
  box-shadow: $cm-shadow-dock;
}

.dlg__title {
  display: block;
  text-align: center;
  font-size: $cm-font-size-lg;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
  line-height: 1.4;
}

.dlg__content {
  display: block;
  margin-top: $cm-space-2;
  text-align: center;
  font-size: $cm-font-size-sm;
  line-height: 1.7;
  color: $cm-color-text-2;
}

/* 明细块：浅底凹槽，与正文拉开一层，价格类信息不跟说明文字混在一起 */
.dlg__lines {
  margin-top: $cm-space-3;
  padding: $cm-space-3;
  background: $cm-color-bg;
  border-radius: $cm-radius-xl;
}

.dlg__line {
  display: flex;
  align-items: baseline;
  gap: $cm-space-2;
  padding: 6rpx 0;
}

.dlg__k {
  flex: none;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.dlg__v {
  flex: 1;
  min-width: 0;
  text-align: right;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  word-break: break-all;
}

.dlg__v--strong {
  font-size: $cm-font-size-md;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-price;
}

.dlg__input {
  margin-top: $cm-space-3;
  height: 88rpx;
  padding: 0 $cm-space-3;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-xl;
}

.dlg__ph {
  color: $cm-color-text-3;
}

.dlg__note {
  display: block;
  margin-top: $cm-space-3;
  font-size: 22rpx;
  line-height: 1.6;
  color: $cm-color-text-3;
}

.dlg__ops {
  display: flex;
  gap: $cm-space-2;
  margin-top: $cm-space-5;
}

.dlg__btn {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $cm-radius-pill;
  font-size: $cm-font-size-base;
  font-weight: $cm-font-weight-bold;
  transition: transform 140ms ease-out, opacity 160ms ease-out;
}

.dlg__btn--on {
  transform: scale(0.96);
  opacity: 0.9;
}

.dlg__btn--ghost {
  color: $cm-color-text-2;
  background: $cm-color-bg;
}

.dlg__btn--primary {
  color: #fff;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: 0 8rpx 18rpx -8rpx rgba(255, 106, 0, 0.8);
}

.dlg__btn--danger {
  color: #fff;
  background-image: linear-gradient(135deg, #FF8A73, $cm-color-err);
  box-shadow: 0 8rpx 18rpx -8rpx rgba(229, 72, 77, 0.7);
}

/* ── 动作菜单：贴底 ─────────────────────────────────────── */
.dlg__sheet {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: $cm-space-3 $cm-space-3 calc(#{$cm-space-3} + env(safe-area-inset-bottom));
  background: $cm-color-bg;
  border-radius: $cm-radius-3xl $cm-radius-3xl 0 0;
}

.dlg__sheet-t {
  display: block;
  padding: $cm-space-2 0 $cm-space-3;
  text-align: center;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.dlg__acts {
  background: $cm-color-surface;
  border-radius: $cm-radius-2xl;
  overflow: hidden;
}

.dlg__act {
  height: 104rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  border-top: 1rpx solid $cm-color-border;
}

.dlg__act:first-child {
  border-top: none;
}

.dlg__act--on {
  background: $cm-color-bg;
}

.dlg__act--cancel {
  margin-top: $cm-space-2;
  border-radius: $cm-radius-2xl;
  border-top: none;
  color: $cm-color-text-2;
  background: $cm-color-surface;
}

@keyframes dlgMask {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes dlgPop {
  from {
    opacity: 0;
    transform: scale(0.9);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@keyframes dlgUp {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}
</style>

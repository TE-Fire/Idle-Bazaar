<script setup lang="ts">
/**
 * 交易评价页（notes/13 O18）
 *
 * <p>三种状态：已评过只展示结果、还能评给表单、窗口关了说明原因。
 * **一单一评**，提交后不能改——公开内容允许回改会变成「先给好评再改差评」的报复手段。
 *
 * <p>被评价人不由本页决定：后端按「订单的对方」推出，前端传了也不作数。
 *
 * <p>星星用 ★ 字符而不是图标：小程序不支持 SVG，为五颗星再画一套 CSS 图标不值得，
 * 字符在任何端都能渲染，选中态换色即可。
 */

import { onLoad } from '@dcloudio/uni-app'
import { computed, ref } from 'vue'
import { userReviewApi, type OrderReviewVO } from '@/api/review'
import { getSession } from '@/utils/session'

const orderId = ref('')
const state = ref<OrderReviewVO | null>(null)
const loading = ref(false)
const submitting = ref(false)

const rating = ref(5)
const tags = ref<string[]>([])
const comment = ref('')

const tagOptions = computed(() => {
  const t = state.value?.tagTable
  return [...(t?.positive ?? []), ...(t?.negative ?? [])]
})

const canSubmit = computed(() => !!state.value?.canReview && !state.value?.mine)

function load(): void {
  if (!getSession().tokenValue) {
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  loading.value = true
  userReviewApi
    .ofOrder(orderId.value)
    .then((vo) => {
      state.value = vo
    })
    .catch(() => undefined)
    .finally(() => {
      loading.value = false
    })
}

onLoad((options) => {
  orderId.value = String(options?.orderId ?? '')
  if (orderId.value) load()
})

function pickTag(label: string): void {
  const list = tags.value
  const idx = list.indexOf(label)
  if (idx >= 0) {
    list.splice(idx, 1)
    return
  }
  // 一次最多 3 个：与后端校验一致，前端先挡一道，避免白跑一次请求
  if (list.length >= 3) {
    void uni.showToast({ title: '最多选 3 个标签', icon: 'none' })
    return
  }
  list.push(label)
}

function submit(): void {
  if (!canSubmit.value || submitting.value) return
  if (rating.value < 1) {
    void uni.showToast({ title: '请选择评分', icon: 'none' })
    return
  }
  submitting.value = true
  userReviewApi
    .submit(orderId.value, {
      rating: rating.value,
      tags: tags.value.length ? tags.value : undefined,
      comment: comment.value.trim() || undefined
    })
    .then(() => {
      void uni.showToast({ title: '评价已提交', icon: 'none' })
      load()
    })
    .catch(() => undefined)
    .finally(() => {
      submitting.value = false
    })
}
</script>

<template>
  <view class="page">
    <view v-if="loading && !state" class="state"><text class="state__t">加载中…</text></view>

    <!-- 已评过：只读展示 -->
    <view v-else-if="state?.mine" class="card">
      <view class="rv__tags">
        <text class="stars">
          <text v-for="i in 5" :key="i" class="star" :class="{ 'star--on': i <= (state.mine.rating || 0) }">★</text>
        </text>
      </view>
      <view v-if="state.mine.tags?.length" class="chips">
        <text v-for="t in state.mine.tags" :key="t" class="chip chip--on">{{ t }}</text>
      </view>
      <text v-if="state.mine.comment" class="rv__text">{{ state.mine.comment }}</text>
      <text class="tip">评价已提交，不能修改。</text>
    </view>

    <!-- 表单：订单已完成且在 30 天窗口内 -->
    <view v-else-if="state?.canReview" class="card">
      <text class="label">评分</text>
      <view class="stars stars--pick">
        <text
          v-for="i in 5"
          :key="i"
          class="star star--lg"
          :class="{ 'star--on': i <= rating }"
          @click="rating = i"
        >
          ★
        </text>
      </view>

      <text class="label">标签（最多 3 个）</text>
      <view class="chips">
        <text
          v-for="t in tagOptions"
          :key="t.label"
          class="chip"
          :class="{ 'chip--on': tags.includes(t.label) }"
          @click="pickTag(t.label)"
        >
          {{ t.label }}
        </text>
      </view>

      <text class="label">说点什么（选填）</text>
      <textarea
        v-model="comment"
        class="input"
        :maxlength="500"
        placeholder="描述是否一致、沟通是否顺畅、面交是否守时…"
      />

      <text v-if="state.expireAt" class="tip">评价截止：{{ state.expireAt }}</text>
      <button class="btn" :disabled="submitting" @click="submit">
        {{ submitting ? '提交中…' : '提交评价' }}
      </button>
    </view>

    <view v-else class="state">
      <text class="state__t">该订单当前不可评价</text>
      <text class="state__s">仅已完成的订单可在面交后 30 天内评价。</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  min-height: 100vh;
  padding: $cm-space-3 $cm-space-4 calc($cm-space-6 + env(safe-area-inset-bottom));
  background: $cm-color-bg;
}

.state {
  padding: $cm-space-8 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $cm-space-2;
}

.state__t {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}

.state__s {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.card {
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
  padding: $cm-space-5;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
}

.label {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.stars {
  display: flex;
  gap: 8rpx;
}

.stars--pick {
  gap: 16rpx;
}

.star {
  font-size: 34rpx;
  color: $cm-color-border;
  line-height: 1;
}

.star--lg {
  font-size: 52rpx;
}

.star--on {
  color: $cm-color-brand;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: $cm-space-2;
}

.chip {
  padding: 6rpx 18rpx;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
  background: $cm-color-bg;
  border-radius: 999rpx;
}

.chip--on {
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
}

.input {
  width: 100%;
  min-height: 160rpx;
  padding: $cm-space-3;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
  box-sizing: border-box;
}

.rv__text {
  font-size: $cm-font-size-sm;
  line-height: 1.7;
  color: $cm-color-text-1;
}

.tip {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.btn {
  margin-top: $cm-space-2;
  height: 88rpx;
  line-height: 88rpx;
  font-size: $cm-font-size-md;
  color: #1b1b21;
  background: $cm-color-brand;
  border-radius: 999rpx;
}
</style>

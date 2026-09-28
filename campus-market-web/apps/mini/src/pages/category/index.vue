<script setup lang="ts">
/**
 * M4 分类页（design/02 §六，P0）
 *
 * <p>左分类树 + 右结果。骨架期只做一级分类，二级（如「教材教辅 → 考研资料」）
 * 等字典接口 `/api/dict/all` 就位后再补——字典必须走接口，三端不得硬编码（tech/02 §七）。
 */

import { computed, ref } from 'vue'
import { CATEGORIES, type Category } from '@campus/shared'
import { mockItems } from '@campus/mock'
import GoodsCard from '@/components/GoodsCard.vue'

const categories = CATEGORIES.filter((c) => c !== '首页') as Category[]
const active = ref<Category>(categories[0])

const list = computed(() => mockItems.filter((it) => it.category === active.value))
</script>

<template>
  <view class="page">
    <scroll-view class="tree" scroll-y>
      <view
        v-for="c in categories"
        :key="c"
        class="node"
        :class="{ 'node--on': active === c }"
        @click="active = c"
      >
        {{ c }}
      </view>
    </scroll-view>

    <scroll-view class="result" scroll-y>
      <view v-if="!list.length" class="empty">
        <text>这座校区还没有人发布「{{ active }}」</text>
      </view>
      <view v-else class="result__in">
        <GoodsCard v-for="it in list" :key="it.id" :item="it" />
      </view>
    </scroll-view>
  </view>
</template>

<style scoped lang="scss">
.page {
  display: flex;
  height: 100vh;
}

.tree {
  width: 200rpx;
  flex: none;
  background: $cm-color-bg;
}

.node {
  height: 96rpx;
  line-height: 96rpx;
  padding-left: $cm-space-4;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

.node--on {
  color: $cm-color-brand-text;
  font-weight: 600;
  background: $cm-color-surface;
  border-left: 6rpx solid $cm-color-brand;
}

.result {
  flex: 1;
  min-width: 0;
  background: $cm-color-surface;
}

.result__in {
  display: flex;
  flex-direction: column;
  gap: $cm-space-2;
  padding: $cm-space-3;
}

.empty {
  padding: 120rpx $cm-space-6;
  text-align: center;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-3;
}
</style>

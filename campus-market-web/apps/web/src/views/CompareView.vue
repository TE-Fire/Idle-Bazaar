<script setup lang="ts">
/**
 * P4 对比页（桌面独占，business/01 §三 F-W2）
 *
 * <p>对比维度固定八项（F-W2 判据）：价格 / 原价 / 成色 / 校区 / 面交点 /
 * 卖家信用 / 发布时间 / 商品标签。**列是商品、行是维度**——横评表要能一眼扫完
 * 同一维度上的差异，转置成「行是商品」就退化成列表了。
 *
 * <p>数据来源优先级：URL 的 `ids` > 对比篮。这样链接可分享，且刷新不丢。
 */

import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { CONDITION_LABELS, formatPrice, formatRelativeTime, type GoodsItem } from '@campus/shared'
import { findItem } from '@campus/mock'
import { useCompareStore } from '@/stores/compare'

const route = useRoute()
const compare = useCompareStore()

const items = computed<GoodsItem[]>(() => {
  const ids = (route.query.ids as string | undefined)?.split(',').filter(Boolean)
  if (ids?.length) return ids.map(findItem).filter((it): it is GoodsItem => !!it)
  return compare.items
})

interface Row {
  key: string
  label: string
  /** 返回该单元格文本；`best` 用于高亮最优项（价格最低 / 成色最好） */
  value: (it: GoodsItem) => string
  best?: (list: GoodsItem[]) => string | null
}

const rows: Row[] = [
  { key: 'price', label: '价格', value: (it) => formatPrice(it.price), best: (l) => String(Math.min(...l.map((i) => i.price))) },
  {
    key: 'original',
    label: '原价',
    value: (it) => (it.originalPrice ? formatPrice(it.originalPrice) : '—')
  },
  { key: 'condition', label: '成色', value: (it) => `${it.condition} · ${CONDITION_LABELS[it.condition]}` },
  { key: 'campus', label: '校区', value: (it) => it.campus },
  { key: 'meet', label: '面交点', value: (it) => it.meetPoint },
  { key: 'credit', label: '卖家信用', value: (it) => `${it.seller.credit}${it.seller.verified ? ' · 已认证' : ''}` },
  { key: 'time', label: '发布时间', value: (it) => formatRelativeTime(it.publishedAt) },
  { key: 'tags', label: '标签', value: (it) => [it.negotiable ? '可议价' : '', it.delivery ? '包配送' : '', it.badge ?? ''].filter(Boolean).join(' / ') || '—' }
]

const bg = (tints: [string, string]): string => `linear-gradient(135deg, ${tints[0]}, ${tints[1]})`
</script>

<template>
  <div class="cm-shell compare">
    <div class="crumb">
      <RouterLink :to="{ name: 'home' }">首页</RouterLink>
      <span> / 商品对比</span>
    </div>

    <el-empty v-if="items.length < 2" description="至少选择 2 件商品才能对比">
      <RouterLink :to="{ name: 'home' }"><el-button type="primary">去挑几件</el-button></RouterLink>
    </el-empty>

    <template v-else>
      <table class="matrix">
        <thead>
          <tr>
            <th class="corner">对比项</th>
            <th v-for="it in items" :key="it.id">
              <span class="thumb" :style="{ background: bg(it.tints) }" />
              <RouterLink :to="{ name: 'item', params: { id: it.id } }" class="t">{{ it.title }}</RouterLink>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.key">
            <th>{{ r.label }}</th>
            <td
              v-for="it in items"
              :key="`${r.key}-${it.id}`"
              :class="{ best: r.best && r.best(items) === r.value(it).replace(/[¥,]/g, '') }"
            >
              {{ r.value(it) }}
            </td>
          </tr>
        </tbody>
      </table>

      <div class="acts">
        <el-button @click="compare.clear()">清空对比篮</el-button>
        <RouterLink :to="{ name: 'home' }"><el-button type="primary">继续挑选</el-button></RouterLink>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.compare {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
}

.crumb {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.matrix {
  width: 100%;
  border-collapse: collapse;
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-xl);
  overflow: hidden;
  font-size: var(--cm-font-size-sm);
}

.matrix th,
.matrix td {
  border-bottom: 1px solid var(--cm-color-border);
  border-right: 1px solid var(--cm-color-border);
  padding: var(--cm-space-3);
  text-align: center;
  color: var(--cm-color-text-2);
}

.matrix th:last-child,
.matrix td:last-child {
  border-right: none;
}

/* 维度列：左对齐、灰底，视觉上从数据区里让出来 */
.matrix tbody th,
.matrix .corner {
  width: 96px;
  background: var(--cm-color-bg);
  color: var(--cm-color-text-3);
  font-weight: 400;
  text-align: left;
}

.matrix thead th {
  vertical-align: top;
  font-weight: 600;
  color: var(--cm-color-text-1);
}

.thumb {
  display: block;
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: var(--cm-radius-md);
  margin-bottom: var(--cm-space-2);
}

.t {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  font-size: var(--cm-font-size-xs);
  font-weight: 400;
  color: var(--cm-color-text-2);
}

/* 最优项：绿字加粗（涨红跌绿这里不适用，比的是好坏不是涨跌） */
.best {
  color: var(--cm-color-ok);
  font-weight: 700;
}

.acts {
  display: flex;
  justify-content: flex-end;
  gap: var(--cm-space-3);
}
</style>

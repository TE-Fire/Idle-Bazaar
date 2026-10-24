<script setup lang="ts">
/**
 * M4 分类页（design/02 §六，P0）
 *
 * <p>左一级分类树 + 右结果。分类走字典接口 `/api/dict/all`（三端不得硬编码，tech/02 §七），
 * 商品走 `/api/item/list` 按一级分类 id 查（后端命中其下所有二级）。
 */

import { computed, onMounted, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { userDictApi, userItemApi, type CategoryVO } from '@/api/user'
import { toGoodsItem } from '@/utils/item-map'
import GoodsCard from '@/components/GoodsCard.vue'
import { pendingCategoryId } from '@/utils/nav-intent'

const roots = ref<CategoryVO[]>([])
const children = ref<Record<string, CategoryVO[]>>({})
const activeId = ref('')
const activeSub = ref('')
const list = ref<ReturnType<typeof toGoodsItem>[]>([])
const loading = ref(false)

const subs = computed(() => children.value[activeId.value] ?? [])

/** 字典只拉一次：onShow 早于 onMounted（首帧从金刚区带意图进入时），两边都要能安全触发 */
let rootsLoaded = false

async function fetchRoots(): Promise<void> {
  if (rootsLoaded) return
  try {
    const dict = await userDictApi.all()
    // 后端契约：一级分类 parentId 置 null（库里是 0，Jackson non_null 后键消失），
    // 所以「无 parentId」即一级；二级按 parentId 归属分组
    roots.value = dict.categories.filter((c) => !c.parentId)
    const map: Record<string, CategoryVO[]> = {}
    for (const c of dict.categories) {
      if (c.parentId) {
        ;(map[c.parentId] ??= []).push(c)
      }
    }
    children.value = map
    rootsLoaded = true
    if (roots.value.length) {
      activeId.value = roots.value[0].id
      activeSub.value = ''
      await fetchList()
    }
  } catch {
    uni.showToast({ title: '字典加载失败', icon: 'none' })
  }
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const res = await userItemApi.list({ categoryId: activeId.value, pageSize: 50 })
    const all = res.list.map(toGoodsItem)
    // 二级 chips 选中时本地再过滤一层（列表接口按一级拉全，二级维度前端筛）
    list.value = activeSub.value ? all.filter((it) => subNameOf(it) === activeSub.value) : all
  } catch {
    uni.showToast({ title: '商品加载失败', icon: 'none' })
  } finally {
    loading.value = false
  }
}

/** 二级名 → 商品分类名匹配（列表 VO 只带中文名，不带二级 id） */
function subNameOf(it: { category: string }): string {
  return it.category
}

async function pickRoot(c: CategoryVO): Promise<void> {
  if (activeId.value === c.id) return
  activeId.value = c.id
  activeSub.value = ''
  await fetchList()
}

async function pickSub(name: string): Promise<void> {
  activeSub.value = activeSub.value === name ? '' : name
  await fetchList()
}

/** 首页金刚区带意图进入：消费后立即清空，意图分类不存在（被停用）时忽略 */
onShow(async () => {
  const id = pendingCategoryId.value
  if (!id) return
  pendingCategoryId.value = ''
  await fetchRoots()
  if (id !== activeId.value && roots.value.some((r) => r.id === id)) {
    activeId.value = id
    activeSub.value = ''
    await fetchList()
  }
})

onMounted(fetchRoots)
</script>

<template>
  <view class="page">
    <scroll-view class="tree" scroll-y>
      <view
        v-for="c in roots"
        :key="c.id"
        class="node"
        :class="{ 'node--on': activeId === c.id }"
        @click="pickRoot(c)"
      >
        {{ c.name }}
      </view>
    </scroll-view>

    <scroll-view class="result" scroll-y>
      <!-- 二级 chips：点选后本地过滤，再点一次取消 -->
      <view v-if="subs.length" class="subs">
        <text
          v-for="s in subs"
          :key="s.id"
          class="sub"
          :class="{ 'sub--on': activeSub === s.name }"
          @click="pickSub(s.name)"
        >
          {{ s.name }}
        </text>
      </view>

      <view v-if="loading" class="empty">
        <text>加载中…</text>
      </view>
      <view v-else-if="!list.length" class="empty">
        <text>这个分类下还没有在售商品</text>
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

.subs {
  display: flex;
  flex-wrap: wrap;
  gap: $cm-space-2;
  padding: $cm-space-3;
}

.sub {
  padding: 0 $cm-space-3;
  height: 48rpx;
  line-height: 48rpx;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
  background: $cm-color-bg;
  border-radius: 24rpx;
}

.sub--on {
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border: 1rpx solid $cm-color-brand;
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

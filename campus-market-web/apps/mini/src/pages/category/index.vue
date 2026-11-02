<script setup lang="ts">
/**
 * M4 分类页（design/02 §六，P0）
 *
 * <p>左一级分类树 + 右结果。分类走字典接口 `/api/dict/all`（三端不得硬编码，tech/02 §七），
 * 商品走 `/api/item/list` 按一级分类 id 查（后端命中其下所有二级）。
 *
 * <p>2026-10-28 视觉改版：左树改成「透明底 + 选中项白色浮起卡片」，竖条指示器按索引
 * 平移动画（不再是每一项各写一条 border-left 导致跳变）；右侧结果用横向商品行卡
 * ——右栏只有 ~540rpx，双列竖卡会把标题挤成两三个字。
 */

import { computed, onMounted, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { userDictApi, userItemApi, type CategoryVO } from '@/api/user'
import { toGoodsItem } from '@/utils/item-map'
import CmIcon from '@/components/CmIcon.vue'
import CmNav from '@/components/CmNav.vue'
import TabBar from '@/components/TabBar.vue'
import { pendingCategoryId } from '@/utils/nav-intent'

const roots = ref<CategoryVO[]>([])
const children = ref<Record<string, CategoryVO[]>>({})
const activeId = ref('')
const activeSub = ref('')
const list = ref<ReturnType<typeof toGoodsItem>[]>([])
const loading = ref(false)

const subs = computed(() => children.value[activeId.value] ?? [])
const activeIndex = computed(() => Math.max(0, roots.value.findIndex((r) => r.id === activeId.value)))
const activeName = computed(() => roots.value.find((r) => r.id === activeId.value)?.name ?? '全部分类')

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

function openItem(id: string): void {
  void uni.navigateTo({ url: `/pages/item/detail?id=${id}` })
}
</script>

<template>
  <view class="cm-page cm-page--tab">
    <CmNav variant="pad" />

    <view class="wrap">
      <!-- ① 左侧一级分类：透明底，选中项浮起成白卡 -->
      <scroll-view class="tree" scroll-y :show-scrollbar="false">
        <!-- 竖条指示器：靠 transform 平移，切换时是滑过去而不是跳过去 -->
        <view class="tree__ind" :style="{ transform: `translateY(${activeIndex * 96}rpx)` }" />
        <view
          v-for="c in roots"
          :key="c.id"
          class="node"
          :class="{ 'node--on': activeId === c.id }"
          hover-class="node--press"
          @click="pickRoot(c)"
        >
          <text>{{ c.name }}</text>
        </view>
      </scroll-view>

      <!-- ② 右侧结果 -->
      <scroll-view class="result" scroll-y :show-scrollbar="false">
        <view class="result__head">
          <text class="result__t">{{ activeName }}</text>
          <text class="result__n">{{ list.length }} 件在售</text>
        </view>

        <!-- 二级 chips：点选后本地过滤，再点一次取消 -->
        <scroll-view v-if="subs.length" class="subs" scroll-x :show-scrollbar="false">
          <view class="subs__in">
            <view
              v-for="s in subs"
              :key="s.id"
              class="sub"
              :class="{ 'sub--on': activeSub === s.name }"
              hover-class="sub--press"
              @click="pickSub(s.name)"
            >
              <text>{{ s.name }}</text>
            </view>
          </view>
        </scroll-view>

        <view v-if="loading" class="rows">
          <view v-for="n in 4" :key="n" class="cm-skel row-skel" />
        </view>
        <view v-else-if="!list.length" class="cm-state">
          <CmIcon name="box" size="lg" tone="muted" />
          <text class="cm-state__t">这个分类下还没有在售商品</text>
          <text class="cm-state__s">换个分类看看</text>
        </view>
        <view v-else class="rows">
          <view
            v-for="(it, i) in list"
            :key="it.id"
            class="row cm-in"
            :style="{ animationDelay: Math.min(i, 8) * 30 + 'ms' }"
            hover-class="row--press"
            @click="openItem(it.id)"
          >
            <view
              class="row__pic"
              :style="
                it.images?.[0]
                  ? { backgroundImage: `url(${it.images[0]})` }
                  : { backgroundImage: `linear-gradient(135deg, ${it.tints[0]}, ${it.tints[1]})` }
              "
            />
            <view class="row__body">
              <text class="row__title cm-ell2">{{ it.title }}</text>
              <view class="row__tags">
                <text class="cm-tag cm-tag--muted">{{ it.condition }}</text>
                <text class="cm-tag cm-tag--brand">{{ it.campus }}</text>
              </view>
              <view class="row__price">
                <text class="row__yen">¥</text>
                <text class="row__num">{{ it.price }}</text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <TabBar active="category" />
  </view>
</template>

<style scoped lang="scss">
.wrap {
  display: flex;
  /* 撑满除底部导航外的一屏，左右各自滚动 */
  height: calc(100vh - #{$cm-layout-tabbar-h} - env(safe-area-inset-bottom));
}

/* ── ① 左树 ─────────────────────────────────────────────── */
.tree {
  position: relative;
  width: 192rpx;
  flex: none;
  padding: $cm-space-3 0 $cm-space-6;
}

/* 竖条压在选中卡左侧，z-index 要高过白卡，否则被卡片底色盖住 */
.tree__ind {
  position: absolute;
  left: 0;
  top: $cm-space-3;
  z-index: 2;
  width: 6rpx;
  height: 96rpx;
  border-radius: 0 6rpx 6rpx 0;
  background-image: linear-gradient(180deg, $cm-color-brand-glow, $cm-color-brand);
  transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
}

.node {
  position: relative;
  height: 96rpx;
  margin-left: 10rpx;
  display: flex;
  align-items: center;
  padding-left: $cm-space-3;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
  border-radius: $cm-radius-xl;
  transition: background 200ms ease-out, color 200ms ease-out, transform 140ms ease-out,
    box-shadow 200ms ease-out;
}

.node--press {
  transform: scale(0.98);
}

/* 选中：白色浮起 + 加粗主色，配合竖条读成「当前这一栏」 */
.node--on {
  color: $cm-color-brand-text;
  font-weight: $cm-font-weight-bold;
  background: $cm-color-surface;
  box-shadow: $cm-shadow-xs;
}

/* ── ② 右侧 ─────────────────────────────────────────────── */
.result {
  flex: 1;
  min-width: 0;
  padding: 0 $cm-space-4;
}

.result__head {
  display: flex;
  align-items: baseline;
  gap: $cm-space-2;
  padding: $cm-space-3 0 $cm-space-2;
}

.result__t {
  font-size: $cm-font-size-lg;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
}

.result__n {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.subs {
  white-space: nowrap;
  padding-bottom: $cm-space-1;
}

.subs__in {
  display: inline-flex;
  gap: $cm-space-2;
  padding: 4rpx 0 $cm-space-2;
}

.sub {
  height: 54rpx;
  padding: 0 $cm-space-3;
  display: flex;
  align-items: center;
  border-radius: $cm-radius-pill;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-2;
  background: $cm-color-surface;
  border: 1rpx solid $cm-color-border;
  transition: background 180ms ease-out, color 180ms ease-out, border-color 180ms ease-out;
}

.sub--press {
  transform: scale(0.95);
}

.sub--on {
  color: $cm-color-brand-text;
  font-weight: $cm-font-weight-bold;
  background: $cm-color-brand-bg;
  border-color: $cm-color-brand;
}

/* ── 横向商品行 ─────────────────────────────────────────── */
.rows {
  display: flex;
  flex-direction: column;
  gap: $cm-space-3;
  padding-bottom: $cm-space-4;
}

.row {
  display: flex;
  gap: $cm-space-3;
  padding: $cm-space-3;
  background: $cm-color-surface;
  border-radius: $cm-radius-2xl;
  box-shadow: $cm-shadow-xs;
  transition: transform 140ms ease-out;
}

.row--press {
  transform: scale(0.98);
}

.row__pic {
  width: 176rpx;
  height: 176rpx;
  border-radius: $cm-radius-xl;
  flex: none;
  background-size: cover;
  background-position: center;
}

.row__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.row__title {
  font-size: $cm-font-size-sm;
  line-height: 1.42;
  color: $cm-color-text-1;
}

.row__tags {
  display: flex;
  gap: 8rpx;
  flex-wrap: wrap;
}

.row__price {
  margin-top: auto;
  display: flex;
  align-items: baseline;
  gap: 2rpx;
}

.row__yen {
  font-size: $cm-font-size-xs;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-price;
}

.row__num {
  font-size: 34rpx;
  font-weight: $cm-font-weight-heavy;
  line-height: 1;
  color: $cm-color-price;
}

.row-skel {
  height: 200rpx;
}
</style>

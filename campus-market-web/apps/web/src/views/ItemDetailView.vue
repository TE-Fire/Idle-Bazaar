<script setup lang="ts">
/**
 * P2 商品详情（business/01 §二 W-B2）
 *
 * <p>校园字段是一等公民（design/01 原则 5）：成色 / 校区 / 面交点 / 学号认证
 * 必须在**卡片、筛选、详情**三处同时可见，所以这里把四项放进参数表而不是折叠起来。
 *
 * <p>数据接 `/api/item/{id}`（免登录）：详情同时是交易入口——「聊一聊」进询单会话，
 * 自己的商品显示编辑入口。无主图时保留渐变占位（骨架期视觉方案）。
 */

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CONDITION_LABELS, formatPrice, formatRelativeTime, type GoodsItem } from '@campus/shared'
import { userItemApi, type ApiItemVO } from '@campus/api'
import { useCompareStore } from '@/stores/compare'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()
const userStore = useUserStore()

const item = ref<ApiItemVO | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    item.value = await userItemApi.detail(String(route.params.id ?? ''))
  } catch {
    // 不存在 / 已下架：http 层已提示，这里落空态
  } finally {
    loading.value = false
  }
})

/** 图集：有真实图片用图片，否则一张渐变占位（切换「图」就是换一组渐变） */
const shots = computed(() => {
  const imgs = item.value?.images ?? []
  return imgs.length ? imgs.map((i) => i.url) : [null]
})
const active = ref(0)
const bg = (flip: boolean): string => {
  const tints: [string, string] = ['#ECEFF3', '#D3D9E0']
  return `linear-gradient(135deg, ${flip ? tints[1] : tints[0]}, ${flip ? tints[0] : tints[1]})`
}

const selected = computed(() => (item.value ? compare.has(item.value.id) : false))
const isMine = computed(() => !!item.value && userStore.userId === item.value.sellerId)

function onCompare(): void {
  if (!item.value) return
  // 对比 store 是 mock 时代的联合类型；真实分类名/校区名不在枚举里，按字符串收
  const goods: GoodsItem = {
    id: item.value.id,
    title: item.value.title,
    category: (item.value.categoryName ?? '其他') as GoodsItem['category'],
    price: Number(item.value.price),
    originalPrice: item.value.originPrice ? Number(item.value.originPrice) : undefined,
    condition: item.value.grade as GoodsItem['condition'],
    campus: (item.value.campusName ?? '') as GoodsItem['campus'],
    meetPoint: item.value.meetPointName ?? '待定',
    negotiable: !!item.value.negotiable,
    delivery: !!item.value.deliveryIncluded,
    status: item.value.status,
    wantCount: 0,
    description: item.value.description ?? '',
    seller: {
      id: item.value.sellerId,
      nick: item.value.sellerNick ?? '卖家',
      verified: true,
      credit: '良好',
      tints: ['#BFD4EA', '#8FA9C4']
    },
    images: [],
    tints: ['#ECEFF3', '#D3D9E0'],
    publishedAt: item.value.publishAt
  }
  if (!compare.toggle(goods)) ElMessage.warning({ message: '最多对比 4 件', duration: 2000 })
}

/** 聊一聊：未登录先跳登录（带回跳）；自己的商品不进会话 */
function onChat(): void {
  if (!item.value) return
  if (!userStore.userId) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  void router.push({ name: 'chat', query: { itemId: item.value.id, peerId: item.value.sellerId } })
}

function onEdit(): void {
  if (!item.value) return
  void router.push({ path: '/publish', query: { edit: item.value.id } })
}
</script>

<template>
  <div v-if="item" class="cm-shell detail">
    <div class="top">
      <!-- 图集 -->
      <div class="gallery">
        <div class="stage" :style="{ background: shots[active] ? undefined : bg(active % 2 === 1) }">
          <img v-if="shots[active]" :src="shots[active]!" alt="" class="stage__img" />
        </div>
        <div class="thumbs">
          <button
            v-for="(s, i) in shots"
            :key="i"
            type="button"
            class="thumb"
            :class="{ on: active === i }"
            :style="{ background: s ? undefined : bg(i % 2 === 1) }"
            :aria-label="`第 ${i + 1} 张图`"
            @click="active = i"
          >
            <img v-if="s" :src="s" alt="" class="thumb__img" />
          </button>
        </div>
      </div>

      <!-- 价格盒 -->
      <div class="box">
        <h1 class="title">{{ item.title }}</h1>

        <div class="tags">
          <span v-if="item.deliveryIncluded" class="cm-tag cm-tag--red">包邮</span>
          <span v-if="item.negotiable" class="cm-tag cm-tag--yellow">可议价</span>
          <span class="cm-tag cm-tag--blue">信用 {{ item.sellerCredit ?? 100 }} 分</span>
        </div>

        <div class="price">
          <strong>{{ formatPrice(Number(item.price)) }}</strong>
          <s v-if="item.originPrice">{{ formatPrice(Number(item.originPrice)) }}</s>
          <span class="want">浏览 {{ item.viewCount }} 次</span>
        </div>

        <dl class="params">
          <dt>成色</dt>
          <dd>{{ item.grade }} 级 · {{ CONDITION_LABELS[item.grade as 'A'] }}</dd>
          <dt>校区</dt>
          <dd>{{ item.campusName }}</dd>
          <dt>面交点</dt>
          <dd>{{ item.meetPointName ?? '待定' }}</dd>
          <dt>发布</dt>
          <dd>{{ formatRelativeTime(item.publishAt) }}</dd>
        </dl>

        <div class="acts">
          <template v-if="isMine">
            <el-button type="primary" size="large" @click="onEdit">编辑商品</el-button>
          </template>
          <template v-else>
            <el-button type="primary" size="large" @click="onChat">聊一聊</el-button>
            <el-button size="large" :type="selected ? 'warning' : 'default'" @click="onCompare">
              {{ selected ? '已加入对比' : '加入对比' }}
            </el-button>
          </template>
        </div>

        <!-- 卖家卡 -->
        <div class="seller">
          <span class="avatar" :style="{ background: bg(false) }" />
          <div class="seller__meta">
            <span class="nick">{{ item.sellerNick ?? '卖家' }}</span>
            <span class="sub">信用 {{ item.sellerCredit ?? 100 }} 分</span>
          </div>
        </div>

        <p class="notice">
          面交须知：请在校园内公共场所交接，先验货后付款；平台不代收货款，
          任何要求站外转账的都请举报。
        </p>
      </div>
    </div>

    <section class="desc">
      <h2>商品描述</h2>
      <p class="desc__text">{{ item.description || '卖家没有填写描述。' }}</p>
    </section>
  </div>

  <!-- 找不到（id 被改过 / 已下架）：给回列表的出口，不留白屏 -->
  <div v-else-if="!loading" class="cm-shell">
    <el-empty description="商品不存在或已下架">
      <el-button type="primary" @click="router.push({ name: 'home' })">回首页逛逛</el-button>
    </el-empty>
  </div>
</template>

<style scoped lang="scss">
.detail {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
  padding-top: var(--cm-space-4);
}

.top {
  display: flex;
  gap: var(--cm-space-6);
  align-items: flex-start;
}

/* ── 图集 ───────────────────────────────────────────────── */

.gallery {
  width: 460px;
  flex: none;
}

.stage {
  aspect-ratio: 4 / 3;
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
  overflow: hidden;
}

.stage__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.thumbs {
  display: flex;
  gap: var(--cm-space-2);
  margin-top: var(--cm-space-3);
}

.thumb {
  width: 84px;
  height: 63px;
  border: 2px solid transparent;
  border-radius: var(--cm-radius-lg);
  cursor: pointer;
  padding: 0;
  overflow: hidden;
}

.thumb.on {
  border-color: var(--cm-color-brand);
}

.thumb__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* ── 价格盒 ─────────────────────────────────────────────── */

.box {
  flex: 1;
  min-width: 0;
  padding: var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
}

.title {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-xl);
  line-height: 1.4;
  font-weight: 600;
}

/* 标题下的卖点标签行：与卡片标题里的 chips 同一套（cm-tag 全局类） */
.tags {
  display: flex;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-3);
}

.price {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-3);
  padding-bottom: var(--cm-space-4);
  border-bottom: 1px solid var(--cm-color-border);
}

.price strong {
  font-size: 28px;
  font-weight: 800;
  line-height: 1.1;
  color: var(--cm-color-price);
}

.price s {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.want {
  margin-left: auto;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.params {
  display: grid;
  grid-template-columns: 72px 1fr 72px 1fr;
  gap: var(--cm-space-2) var(--cm-space-4);
  margin: var(--cm-space-4) 0;
  font-size: var(--cm-font-size-sm);
}

.params dt {
  color: var(--cm-color-text-3);
}

.params dd {
  margin: 0;
  color: var(--cm-color-text-1);
}

.acts {
  display: flex;
  gap: var(--cm-space-3);
  margin-bottom: var(--cm-space-5);
}

/* 详情页主行动按钮高 46px（design/01 §4.2） */
.acts :deep(.el-button--large) {
  height: 46px;
  padding: 0 var(--cm-space-8);
}

.seller {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  padding: var(--cm-space-3) var(--cm-space-4);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-lg);
}

.avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  flex: none;
}

.seller__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-right: auto;
}

.nick {
  font-weight: 600;
}

.sub {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.sub em {
  font-style: normal;
  color: var(--cm-color-ok);
}

.notice {
  margin: var(--cm-space-4) 0 0;
  font-size: var(--cm-font-size-xs);
  line-height: 1.6;
  color: var(--cm-color-text-3);
}

/* ── 描述 ───────────────────────────────────────────────── */

.desc {
  padding: var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
}

.desc h2 {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-md);
  font-weight: 600;
}

.desc__text {
  margin: 0;
  white-space: pre-line;
  line-height: 1.6;
  color: var(--cm-color-text-2);
}

@media (max-width: 1023px) {
  .top {
    flex-direction: column;
  }

  .gallery {
    width: 100%;
  }
}
</style>

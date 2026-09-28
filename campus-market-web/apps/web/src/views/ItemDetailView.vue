<script setup lang="ts">
/**
 * P2 商品详情（business/01 §二 W-B2）
 *
 * <p>校园字段是一等公民（design/01 原则 5）：成色 / 校区 / 面交点 / 学号认证
 * 必须在**卡片、筛选、详情**三处同时可见，所以这里把四项放进参数表而不是折叠起来。
 */

import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CONDITION_LABELS, formatPrice, formatRelativeTime } from '@campus/shared'
import { findItem } from '@campus/mock'
import { useCompareStore } from '@/stores/compare'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()

const item = computed(() => findItem(String(route.params.id ?? '')))

/** 图集：骨架期用占位渐变切换，「图」就是换一组渐变 */
const active = ref(0)
const shots = computed(() => [0, 1, 2, 3])
const bg = (tints: [string, string], flip: boolean): string => {
  const [a, b] = flip ? [tints[1], tints[0]] : tints
  return `linear-gradient(135deg, ${a}, ${b})`
}

const selected = computed(() => (item.value ? compare.has(item.value.id) : false))

function onCompare(): void {
  if (!item.value) return
  if (!compare.toggle(item.value)) ElMessage.warning({ message: '最多对比 4 件', duration: 2000 })
}

function onWant(): void {
  // 下单 / 议价都依赖后端订单与会话接口，骨架期只做反馈
  ElMessage.success({ message: '已通知卖家，可在「消息」里继续沟通', duration: 2000 })
}
</script>

<template>
  <div v-if="item" class="cm-shell detail">
    <div class="crumb">
      <RouterLink :to="{ name: 'home' }">首页</RouterLink>
      <span> / {{ item.category }} / {{ item.title }}</span>
    </div>

    <div class="top">
      <!-- 图集 -->
      <div class="gallery">
        <div class="stage" :style="{ background: bg(item.tints, active % 2 === 1) }" />
        <div class="thumbs">
          <button
            v-for="i in shots"
            :key="i"
            type="button"
            class="thumb"
            :class="{ on: active === i - 1 }"
            :style="{ background: bg(item.tints, (i - 1) % 2 === 1) }"
            :aria-label="`第 ${i} 张图`"
            @click="active = i - 1"
          />
        </div>
      </div>

      <!-- 价格盒 -->
      <div class="box">
        <h1 class="title">{{ item.title }}</h1>

        <div class="price">
          <strong>{{ formatPrice(item.price) }}</strong>
          <s v-if="item.originalPrice">{{ formatPrice(item.originalPrice) }}</s>
          <span class="want">{{ item.wantCount }} 人想要</span>
        </div>

        <dl class="params">
          <dt>成色</dt>
          <dd>{{ item.condition }} 级 · {{ CONDITION_LABELS[item.condition] }}</dd>
          <dt>校区</dt>
          <dd>{{ item.campus }}</dd>
          <dt>面交点</dt>
          <dd>{{ item.meetPoint }}</dd>
          <dt>发布</dt>
          <dd>{{ formatRelativeTime(item.publishedAt) }}</dd>
        </dl>

        <div class="acts">
          <el-button type="primary" size="large" @click="onWant">我想要</el-button>
          <el-button size="large" :type="selected ? 'warning' : 'default'" @click="onCompare">
            {{ selected ? '已加入对比' : '加入对比' }}
          </el-button>
        </div>

        <!-- 卖家卡 -->
        <div class="seller">
          <span class="avatar" :style="{ background: bg(item.seller.tints, false) }" />
          <div class="seller__meta">
            <span class="nick">{{ item.seller.nick }}</span>
            <span class="sub">
              信用{{ item.seller.credit }}
              <em v-if="item.seller.verified">· 学号已认证</em>
            </span>
          </div>
          <el-button size="small" plain>查看主页</el-button>
        </div>

        <p class="notice">
          面交须知：请在校园内公共场所交接，先验货后付款；平台不代收货款，
          任何要求站外转账的都请举报。
        </p>
      </div>
    </div>

    <section class="desc">
      <h2>商品描述</h2>
      <p class="desc__text">{{ item.description }}</p>
    </section>
  </div>

  <!-- 找不到（id 被改过 / 已下架）：给回列表的出口，不留白屏 -->
  <div v-else class="cm-shell">
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
}

.crumb {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
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
  border-radius: var(--cm-radius-xl);
  border: 1px solid var(--cm-color-border);
}

.thumbs {
  display: flex;
  gap: var(--cm-space-2);
  margin-top: var(--cm-space-3);
}

.thumb {
  width: 84px;
  height: 63px;
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-md);
  cursor: pointer;
  padding: 0;
}

.thumb.on {
  border-color: var(--cm-color-brand);
  border-width: 2px;
}

/* ── 价格盒 ─────────────────────────────────────────────── */

.box {
  flex: 1;
  min-width: 0;
  padding: var(--cm-space-5);
  background: var(--cm-color-surface);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-xl);
}

.title {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-xl);
  line-height: 1.4;
  font-weight: 600;
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
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-xl);
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

<script setup lang="ts">
/**
 * 动态卡片（广场流与个人主页动态 Tab 共用）
 *
 * <p>结构固定四段：作者行 / 正文 / 媒体（九图或商品分享卡）/ 互动条。
 * 广场流和详情页共用同一张卡——详情页只是额外挂了评论区，卡片本身不长第二套视觉。
 *
 * <p>点赞只发事件不自己请求：同一条动态可能在列表里出现两次（广场 + 主页），
 * 状态必须由持有列表的那一层统一改，否则两处会显示不一致。
 *
 * <p>图片排版按张数分档（1 张给形状自适应、2/4 张两列、其余三列），
 * 这是社交流的通行做法：一张横图不该被切成正方形格子。
 */

import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { formatRelativeTime } from '@campus/shared'
import type { MomentVO } from '@campus/api'
import { useUserStore } from '@/stores/user'

const props = withDefaults(
  defineProps<{
    moment: MomentVO
    /** 详情页里为 true：卡片本体不可再点进详情，评论区由父级渲染 */
    inDetail?: boolean
  }>(),
  { inDetail: false }
)

const emit = defineEmits<{
  (e: 'toggle-like', m: MomentVO): void
  (e: 'comment', m: MomentVO): void
  (e: 'report', m: MomentVO): void
}>()

const router = useRouter()
const userStore = useUserStore()

/** 自己的动态不显示举报入口——后端也会拒（3006），但按钮不该先给出来 */
const isMine = computed(() => Boolean(userStore.userId) && userStore.userId === props.moment.userId)

const author = computed(() => props.moment.author)
const nickname = computed(() => author.value?.nickname || '校园用户')
const initial = computed(() => nickname.value.slice(0, 1))
const images = computed(() => props.moment.images ?? [])

/** 图片格子档位：张数决定列数与单图比例 */
const galleryClass = computed(() => {
  const n = images.value.length
  if (n === 1) return 'gallery--one'
  if (n === 2 || n === 4) return 'gallery--two'
  return 'gallery--three'
})

const timeText = computed(() => (props.moment.createTime ? formatRelativeTime(props.moment.createTime) : ''))

function goProfile(): void {
  void router.push({ name: 'user-profile', params: { id: props.moment.userId } })
}

function goItem(): void {
  if (props.moment.itemId) void router.push({ name: 'item', params: { id: props.moment.itemId } })
}

function goDetail(): void {
  if (props.inDetail) return
  void router.push({ name: 'moment-detail', params: { id: props.moment.id } })
}

function onComment(): void {
  if (props.inDetail) {
    emit('comment', props.moment)
    return
  }
  goDetail()
}
</script>

<template>
  <article class="mc">
    <!-- 作者行：头像 / 昵称 / 时间，右上是类型标 -->
    <header class="mc__head">
      <button type="button" class="mc__ava" @click="goProfile">
        <img v-if="author?.avatarUrl" :src="author.avatarUrl" alt="" />
        <span v-else>{{ initial }}</span>
      </button>
      <div class="mc__who">
        <button type="button" class="mc__nick" @click="goProfile">{{ nickname }}</button>
        <p class="mc__sub">
          <span v-if="author?.authStatus === 2" class="mc__ok">已认证</span>
          <span>{{ timeText }}</span>
        </p>
      </div>
      <span v-if="moment.type === 2" class="mc__kind">分享好物</span>
    </header>

    <!-- 正文：可点进详情；空正文不占位 -->
    <p v-if="moment.content" class="mc__text" :class="{ 'mc__text--clickable': !inDetail }" @click="goDetail">
      {{ moment.content }}
    </p>

    <!-- 商品分享卡：挂着商品就有，点击进商品详情（优先于动态详情） -->
    <div v-if="moment.itemId" class="share" @click="goItem">
      <div class="share__img">
        <img v-if="moment.itemMainImage" :src="moment.itemMainImage" alt="" />
      </div>
      <div class="share__meta">
        <p class="share__title">{{ moment.itemTitle || '在售闲置' }}</p>
        <p class="share__price">¥{{ moment.itemPrice ?? '—' }}</p>
      </div>
    </div>

    <!-- 九图：按张数换档，点图同样进详情 -->
    <div v-if="images.length" class="gallery" :class="galleryClass">
      <div v-for="(src, i) in images" :key="i" class="gallery__cell" @click="goDetail">
        <img :src="src" alt="" loading="lazy" />
      </div>
    </div>

    <!-- 互动条：点赞高亮只换色，不做弹跳动画（列表里几十个一起动很吵） -->
    <footer class="mc__act">
      <button type="button" class="act" :class="{ 'act--on': moment.liked }" @click="emit('toggle-like', moment)">
        <span class="act__ico">{{ moment.liked ? '♥' : '♡' }}</span>
        <span class="act__n">{{ moment.likeCount && Number(moment.likeCount) > 0 ? moment.likeCount : '赞' }}</span>
      </button>
      <button type="button" class="act" @click="onComment">
        <span class="act__ico">💬</span>
        <span class="act__n">{{ moment.commentCount && Number(moment.commentCount) > 0 ? moment.commentCount : '评论' }}</span>
      </button>
      <button v-if="!isMine" type="button" class="act act--right" @click="emit('report', moment)">
        <span class="act__ico">⚑</span>
        <span class="act__n">举报</span>
      </button>
    </footer>
  </article>
</template>

<style scoped lang="scss">
.mc {
  padding: var(--cm-space-4) var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-lg);
}

/* ── 作者行 ─────────────────────────────────────────────── */

.mc__head {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.mc__ava {
  width: 38px;
  height: 38px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  overflow: hidden;
  font-size: 16px;
  font-weight: 700;
  color: var(--cm-color-brand-text);
  background: var(--cm-color-brand-bg);
  border: none;
  border-radius: 50%;
  cursor: pointer;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.mc__who {
  flex: 1;
  min-width: 0;
}

.mc__nick {
  padding: 0;
  font-size: var(--cm-font-size-sm);
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: none;
  border: none;
  cursor: pointer;

  &:hover {
    color: var(--cm-color-brand-text);
  }
}

.mc__sub {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
  margin: 2px 0 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.mc__ok {
  padding: 0 6px;
  color: var(--cm-color-ok);
  background: #f0faf4;
  border-radius: var(--cm-radius-sm);
}

.mc__kind {
  flex: none;
  padding: 2px 8px;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-brand-text);
  background: var(--cm-color-brand-bg);
  border-radius: var(--cm-radius-sm);
}

/* ── 正文与媒体 ─────────────────────────────────────────── */

.mc__text {
  margin: var(--cm-space-3) 0 0;
  font-size: var(--cm-font-size-md);
  line-height: 1.7;
  color: var(--cm-color-text-1);
  white-space: pre-wrap;
  word-break: break-word;

  &--clickable {
    cursor: pointer;
  }
}

.share {
  display: flex;
  gap: var(--cm-space-3);
  margin-top: var(--cm-space-3);
  padding: var(--cm-space-3);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-md);
  cursor: pointer;

  &:hover {
    background: #f0f0f2;
  }
}

.share__img {
  width: 56px;
  height: 56px;
  flex: none;
  overflow: hidden;
  background: var(--cm-color-border);
  border-radius: var(--cm-radius-sm);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.share__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.share__title {
  margin: 0;
  overflow: hidden;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share__price {
  margin: 0;
  font-size: var(--cm-font-size-md);
  font-weight: 800;
  color: var(--cm-color-price, #ff4400);
}

.gallery {
  display: grid;
  gap: 6px;
  margin-top: var(--cm-space-3);

  &--one {
    grid-template-columns: 1fr;
    max-width: 320px;
  }

  &--two {
    grid-template-columns: repeat(2, 1fr);
    max-width: 420px;
  }

  &--three {
    grid-template-columns: repeat(3, 1fr);
  }
}

.gallery__cell {
  overflow: hidden;
  border-radius: var(--cm-radius-sm);
  background: var(--cm-color-bg);
  cursor: pointer;

  img {
    display: block;
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  /* 单图与双图给固定比例；三列格子统一正方形，避免流里高度乱跳 */
  .gallery--one & {
    aspect-ratio: 4 / 3;
  }

  .gallery--two & {
    aspect-ratio: 1;
  }

  .gallery--three & {
    aspect-ratio: 1;
  }
}

/* ── 互动条 ─────────────────────────────────────────────── */

.mc__act {
  display: flex;
  gap: var(--cm-space-5);
  margin-top: var(--cm-space-3);
  padding-top: var(--cm-space-3);
  border-top: 1px solid var(--cm-color-border);
}

.act {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  background: none;
  border: none;
  cursor: pointer;

  &:hover {
    color: var(--cm-color-text-1);
  }

  &--on {
    color: #e0245e;
    font-weight: 700;
  }

  &--right {
    margin-left: auto;
    color: var(--cm-color-text-3);
  }
}

.act__ico {
  font-size: 15px;
  line-height: 1;
}

.act__n {
  font-variant-numeric: tabular-nums;
}
</style>

<script setup lang="ts">
/**
 * 动态卡片（广场流与个人主页动态 Tab 共用）
 *
 * <p>四段结构：作者行 / 正文 / 媒体（九图或商品分享卡）/ 互动条。
 * 图片按张数分三档排版：一张给宽幅（横图不该被切成方格子），2/4 张两列，其余三列。
 *
 * <p>点赞只发事件不自己请求：同一条动态可能同时出现在广场和主页，
 * 状态必须由持有列表的那一层统一改，否则两处显示会不一致。
 *
 * <p>点击区域三分为：作者进主页、内容进详情、互动条各自处理——
 * 整张卡都可点会让点赞必然先触发跳转。
 */

import { computed } from 'vue'
import { formatRelativeTime } from '@campus/shared'
import { REPORT_TARGET, reportTarget } from '@/utils/report'
import { getSession } from '@/utils/session'
import CmIcon from './CmIcon.vue'
import type { MomentVO } from '@/api/moment'

const props = withDefaults(defineProps<{ moment: MomentVO; inDetail?: boolean }>(), { inDetail: false })
const emit = defineEmits<{ (e: 'toggle-like', m: MomentVO): void }>()

/** 自己的动态不给举报入口（后端也会拒 3006，但按钮不该先给出来） */
const isMine = computed(() => {
  const me = getSession().userId
  return Boolean(me) && me === props.moment.userId
})

const author = computed(() => props.moment.author)
const nickname = computed(() => author.value?.nickname || '校园用户')
const initial = computed(() => nickname.value.slice(0, 1))
const images = computed(() => props.moment.images ?? [])

const galleryClass = computed(() => {
  const n = images.value.length
  if (n === 1) return 'gallery--one'
  if (n === 2 || n === 4) return 'gallery--two'
  return 'gallery--three'
})

const timeText = computed(() =>
  props.moment.createTime ? formatRelativeTime(props.moment.createTime) : ''
)

function goProfile(): void {
  void uni.navigateTo({ url: `/pages/user/index?userId=${props.moment.userId}` })
}

function goItem(): void {
  if (props.moment.itemId) void uni.navigateTo({ url: `/pages/item/detail?id=${props.moment.itemId}` })
}

function goDetail(): void {
  if (props.inDetail) return
  void uni.navigateTo({ url: `/pages/moment/detail?id=${props.moment.id}` })
}

function previewImage(i: number): void {
  uni.previewImage({ urls: images.value, current: images.value[i] })
}
</script>

<template>
  <view class="mc">
    <view class="mc__head">
      <view class="mc__ava" hover-class="mc__ava--active" @click="goProfile">
        <image v-if="author?.avatarUrl" class="mc__ava-img" :src="author.avatarUrl" mode="aspectFill" />
        <text v-else class="mc__ava-t">{{ initial }}</text>
      </view>
      <view class="mc__who" @click="goProfile">
        <view class="mc__top">
          <text class="mc__nick">{{ nickname }}</text>
          <text v-if="author?.authStatus === 2" class="mc__ok">已认证</text>
        </view>
        <text class="mc__time">{{ timeText }}</text>
      </view>
      <text v-if="moment.type === 2" class="mc__kind">分享好物</text>
    </view>

    <text v-if="moment.content" class="mc__text" @click="goDetail">{{ moment.content }}</text>

    <!-- 分享商品卡：点击进商品详情，优先于动态详情 -->
    <view v-if="moment.itemId" class="share" hover-class="share--active" @click="goItem">
      <image v-if="moment.itemMainImage" class="share__img" :src="moment.itemMainImage" mode="aspectFill" />
      <view class="share__meta">
        <text class="share__title">{{ moment.itemTitle || '在售闲置' }}</text>
        <text class="share__price">¥{{ moment.itemPrice ?? '—' }}</text>
      </view>
    </view>

    <!-- 九图：点图看大图（预览），不跳详情 -->
    <view v-if="images.length" class="gallery" :class="galleryClass">
      <view v-for="(src, i) in images" :key="i" class="gallery__cell">
        <image class="gallery__img" :src="src" mode="aspectFill" @click="previewImage(i)" />
      </view>
    </view>

    <view class="mc__act">
      <view class="act" :class="{ 'act--on': moment.liked }" hover-class="act--active" @click="emit('toggle-like', moment)">
        <CmIcon name="heart" size="sm" :tone="moment.liked ? 'brand' : 'muted'" />
        <text class="act__n">{{ Number(moment.likeCount) > 0 ? moment.likeCount : '赞' }}</text>
      </view>
      <view class="act" hover-class="act--active" @click="goDetail">
        <CmIcon name="chat" size="sm" tone="muted" />
        <text class="act__n">{{ Number(moment.commentCount) > 0 ? moment.commentCount : '评论' }}</text>
      </view>
      <view
        v-if="!isMine"
        class="act act--right"
        hover-class="act--active"
        @click="reportTarget(REPORT_TARGET.MOMENT, moment.id)"
      >
        <text class="act__n act__n--quiet">举报</text>
      </view>
    </view>
  </view>
</template>

<style scoped lang="scss">
.mc {
  padding: $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-2xl;
  box-shadow: $cm-shadow-sm;
}

/* ── 作者行 ─────────────────────────────────────────────── */

.mc__head {
  display: flex;
  align-items: center;
  gap: $cm-space-3;
}

.mc__ava {
  width: 72rpx;
  height: 72rpx;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: $cm-color-brand-bg;
  border-radius: 50%;
}

.mc__ava--active {
  opacity: 0.8;
}

.mc__ava-img {
  width: 100%;
  height: 100%;
}

.mc__ava-t {
  font-size: 30rpx;
  font-weight: 700;
  color: $cm-color-brand-text;
}

.mc__who {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.mc__top {
  display: flex;
  align-items: center;
  gap: $cm-space-2;
}

.mc__nick {
  font-size: $cm-font-size-sm;
  font-weight: 700;
  color: $cm-color-text-1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mc__ok {
  flex: none;
  padding: 0 8rpx;
  font-size: 20rpx;
  color: $cm-color-ok;
  background: #f0faf4;
  border-radius: 6rpx;
}

.mc__time {
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
}

.mc__kind {
  flex: none;
  padding: 4rpx 12rpx;
  font-size: 20rpx;
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border-radius: 8rpx;
}

/* ── 正文与媒体 ─────────────────────────────────────────── */

.mc__text {
  display: block;
  margin-top: $cm-space-3;
  font-size: $cm-font-size-base;
  line-height: 1.7;
  color: $cm-color-text-1;
  word-break: break-all;
}

.share {
  display: flex;
  gap: $cm-space-3;
  margin-top: $cm-space-3;
  padding: $cm-space-3;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.share--active {
  opacity: 0.85;
}

.share__img {
  width: 112rpx;
  height: 112rpx;
  flex: none;
  border-radius: $cm-radius-sm;
}

.share__meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6rpx;
}

.share__title {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.share__price {
  font-size: $cm-font-size-md;
  font-weight: 700;
  color: $cm-color-price;
}

.gallery {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-top: $cm-space-3;
}

.gallery__cell {
  overflow: hidden;
  border-radius: $cm-radius-sm;
  background: $cm-color-bg;
}

.gallery__img {
  width: 100%;
  height: 100%;
  display: block;
}

/* 一张：宽幅横图；两/四张：两列；其余：三列 */
.gallery--one .gallery__cell {
  width: 460rpx;
  height: 340rpx;
}

.gallery--two .gallery__cell {
  width: 300rpx;
  height: 300rpx;
}

.gallery--three .gallery__cell {
  width: 206rpx;
  height: 206rpx;
}

/* ── 互动条 ─────────────────────────────────────────────── */

.mc__act {
  display: flex;
  gap: $cm-space-8;
  margin-top: $cm-space-3;
  padding-top: $cm-space-3;
  border-top: 1rpx solid $cm-color-border;
}

.act {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.act--active {
  opacity: 0.6;
}

.act--right {
  margin-left: auto;
}

.act__ico {
  font-size: 28rpx;
  line-height: 1;
}

.act__n {
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
}

/* 举报是低频负向操作：只留一行灰字，不配图标，避免和点赞抢注意力 */
.act__n--quiet {
  color: $cm-color-text-3;
  font-size: $cm-font-size-xs;
}

.act--on .act__n {
  color: $cm-color-brand-2;
  font-weight: $cm-font-weight-bold;
}

.act--on .act__ico {
  color: $cm-color-err;
}
</style>

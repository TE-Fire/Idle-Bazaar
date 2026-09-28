<script setup lang="ts">
/**
 * M3 发布闲置（design/02 §六，P0）
 *
 * <p>与 Web 端 P3 的区别：桌面靠**拖拽 + 实时预览**兑现价值，小程序只能靠
 * `uni.chooseMedia` 选图（≤9），所以这里的重点是**选图后立刻能看到缩略图与封面**，
 * 而不是拖拽排序。表单字段与 Web 端一致，枚举取自 `@campus/shared`。
 *
 * <p>骨架期：图片只走本地临时路径，不上传（MinIO 直传接口未实现）；
 * 提交只给 Toast 反馈，不落库。
 */

import { computed, ref } from 'vue'
import { CAMPUSES, CATEGORIES, CONDITION_GRADES, CONDITION_LABELS, type ConditionGrade } from '@campus/shared'

const MAX_SHOTS = 9

const shots = ref<string[]>([])
const form = ref({
  title: '',
  categoryIndex: 1,
  condition: 'B' as ConditionGrade,
  price: '',
  originalPrice: '',
  campusIndex: 0,
  meetPoint: '',
  description: '',
  negotiable: false,
  delivery: false
})

/** picker 的 range 必须是字符串数组；分类里去掉「首页」 */
const categoryRange = CATEGORIES.filter((c) => c !== '首页')
const campusRange = [...CAMPUSES]

const canSubmit = computed(() => form.value.title.trim().length >= 4 && Number(form.value.price) > 0)

function chooseImage(): void {
  uni.chooseMedia({
    count: MAX_SHOTS - shots.value.length,
    mediaType: ['image'],
    success: (res) => {
      // res.tempFiles 的类型在 @dcloudio/types 里较松，这里显式取路径
      const files = (res as unknown as { tempFiles: { tempFilePath: string }[] }).tempFiles
      shots.value.push(...files.map((f) => f.tempFilePath).slice(0, MAX_SHOTS - shots.value.length))
    }
  })
}

function removeShot(index: number): void {
  shots.value.splice(index, 1)
}

function onCategoryChange(e: { detail: { value: number } }): void {
  form.value.categoryIndex = e.detail.value
}

function onCampusChange(e: { detail: { value: number } }): void {
  form.value.campusIndex = e.detail.value
}

function submit(): void {
  if (!canSubmit.value) {
    uni.showToast({ title: '标题至少 4 字，价格要大于 0', icon: 'none', duration: 1500 })
    return
  }
  // 骨架期不落库：后端 /api/item/publish 未实现
  uni.showToast({ title: '已提交审核（骨架期未落库）', icon: 'none', duration: 1500 })
  setTimeout(() => uni.switchTab({ url: '/pages/index/index' }), 1200)
}
</script>

<template>
  <view class="page">
    <!-- 选图 -->
    <view class="sec">
      <text class="sec__t">图片（{{ shots.length }}/{{ MAX_SHOTS }}，第一张为封面）</text>
      <view class="shots">
        <view v-for="(s, i) in shots" :key="s" class="shot">
          <image class="shot__img" :src="s" mode="aspectFill" />
          <text v-if="i === 0" class="shot__cover">封面</text>
          <text class="shot__x" @click="removeShot(i)">×</text>
        </view>
        <view v-if="shots.length < MAX_SHOTS" class="shot shot--add" @click="chooseImage">
          <text class="shot__plus">＋</text>
        </view>
      </view>
    </view>

    <!-- 表单 -->
    <view class="sec">
      <text class="sec__t">标题</text>
      <input v-model="form.title" class="input" placeholder="例如：iPad 9 64G WiFi 版 电池 92%" maxlength="40" />
    </view>

    <view class="sec">
      <text class="sec__t">分类</text>
      <picker mode="selector" :range="categoryRange" :value="form.categoryIndex" @change="onCategoryChange">
        <view class="picker">{{ categoryRange[form.categoryIndex] }} ▾</view>
      </picker>
    </view>

    <view class="sec">
      <text class="sec__t">成色</text>
      <view class="grades">
        <text
          v-for="g in CONDITION_GRADES"
          :key="g"
          class="grade"
          :class="{ 'grade--on': form.condition === g }"
          @click="form.condition = g"
        >
          {{ g }} {{ CONDITION_LABELS[g] }}
        </text>
      </view>
    </view>

    <view class="sec">
      <text class="sec__t">价格（元）</text>
      <view class="row">
        <input v-model="form.price" class="input" type="digit" placeholder="现价" />
        <input v-model="form.originalPrice" class="input" type="digit" placeholder="原价（选填）" />
      </view>
    </view>

    <view class="sec">
      <text class="sec__t">校区 / 面交点</text>
      <picker mode="selector" :range="campusRange" :value="form.campusIndex" @change="onCampusChange">
        <view class="picker">{{ campusRange[form.campusIndex] }} ▾</view>
      </picker>
      <input v-model="form.meetPoint" class="input" placeholder="面交点，例如：图书馆东门" />
    </view>

    <view class="sec">
      <text class="sec__t">描述</text>
      <textarea v-model="form.description" class="textarea" placeholder="成色细节、购买渠道、瑕疵说明…" />
    </view>

    <view class="sec">
      <view class="switch-row">
        <text>可议价</text>
        <switch :checked="form.negotiable" color="#FF6A00" @change="form.negotiable = $event.detail.value" />
      </view>
      <view class="switch-row">
        <text>包校园配送</text>
        <switch :checked="form.delivery" color="#FF6A00" @change="form.delivery = $event.detail.value" />
      </view>
    </view>

    <view class="submit" hover-class="submit--active" :class="{ 'submit--off': !canSubmit }" @click="submit">
      <text class="submit__t">提交送审</text>
    </view>
  </view>
</template>

<style scoped lang="scss">
.page {
  padding: $cm-space-3 $cm-space-3 160rpx;
}

.sec {
  margin-bottom: $cm-space-3;
  padding: $cm-space-4;
  background: $cm-color-surface;
  border-radius: $cm-radius-lg;
}

.sec__t {
  display: block;
  margin-bottom: $cm-space-3;
  font-size: $cm-font-size-sm;
  font-weight: 600;
  color: $cm-color-text-1;
}

/* ── 选图 ───────────────────────────────────────────────── */

.shots {
  display: flex;
  flex-wrap: wrap;
  gap: $cm-space-2;
}

.shot {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  border-radius: $cm-radius-md;
  overflow: hidden;
}

.shot__img {
  width: 100%;
  height: 100%;
}

.shot--add {
  display: flex;
  align-items: center;
  justify-content: center;
  background: $cm-color-bg;
  border: 1rpx dashed $cm-color-border-strong;
}

.shot__plus {
  font-size: 56rpx;
  color: $cm-color-text-3;
}

.shot__cover {
  position: absolute;
  left: 4rpx;
  top: 4rpx;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-1;
  background: $cm-color-brand;
  padding: 0 6rpx;
  border-radius: $cm-radius-sm;
}

.shot__x {
  position: absolute;
  right: 4rpx;
  top: 0;
  font-size: 36rpx;
  color: #fff;
  text-shadow: 0 0 4rpx rgba(0, 0, 0, 0.6);
}

/* ── 表单 ───────────────────────────────────────────────── */

.input {
  height: 72rpx;
  padding: 0 $cm-space-3;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.row {
  display: flex;
  gap: $cm-space-2;
}

.row .input {
  flex: 1;
  min-width: 0;
}

.picker {
  height: 72rpx;
  line-height: 72rpx;
  padding: 0 $cm-space-3;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
  margin-bottom: $cm-space-2;
}

.grades {
  display: flex;
  flex-wrap: wrap;
  gap: $cm-space-2;
}

.grade {
  padding: 0 $cm-space-3;
  height: 64rpx;
  line-height: 64rpx;
  font-size: $cm-font-size-sm;
  color: $cm-color-text-2;
  background: $cm-color-bg;
  border: 1rpx solid transparent;
  border-radius: $cm-radius-md;
}

/* 选中态是主色仅有的落点之一（design/02 原则 5） */
.grade--on {
  color: $cm-color-brand-text;
  background: $cm-color-brand-bg;
  border-color: $cm-color-brand;
}

.textarea {
  width: 100%;
  height: 180rpx;
  padding: $cm-space-3;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
  background: $cm-color-bg;
  border-radius: $cm-radius-md;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 72rpx;
  font-size: $cm-font-size-base;
  color: $cm-color-text-1;
}

/* ── 提交 ───────────────────────────────────────────────── */

.submit {
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
  background: $cm-color-brand;
  border-radius: $cm-radius-md;
  transition: transform 120ms ease-out;
}

.submit__t {
  font-size: $cm-font-size-md;
  font-weight: 600;
  color: #fff;
}

.submit--off {
  background: $cm-color-brand-bg;
}

.submit--off .submit__t {
  color: $cm-color-text-3;
}

.submit--active {
  transform: scale(0.97);
}
</style>

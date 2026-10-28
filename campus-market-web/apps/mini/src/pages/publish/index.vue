<script setup lang="ts">
/**
 * M3 发布闲置（design/02 §六，P0）
 *
 * <p>与 Web 端 P3 同一契约：**存草稿**（draft=true）与**提交送审**（draft=false）
 * 两个按钮。必填字段（标题 5–50 字 / 价格 / 分类 / 成色 / 校区）两种意图都查——
 * `item` 表这些列 NOT NULL，草稿同样要落库；草稿豁免图片与描述。
 * 编辑模式（`?edit={id}`）下草稿商品显示两个按钮，已发布商品只有「保存」。
 *
 * <p>图片两步票据制上传（utils/upload，固定 PROXY：uni.uploadFile 只能发 multipart，
 * 发不了 Web 端 DIRECT 的 PUT 直传——平台限制）。先占位「上传中」，成功后换 accessUrl；
 * 提交时 images = 已有图片 + 新上传 URL 整体替换。
 */

import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { userDictApi, userItemApi, type CategoryVO } from '@/api/user'
import TabBar from '@/components/TabBar.vue'
import { uploadImage } from '@/utils/upload'

const MAX_SHOTS = 9

/** 选图：先以临时路径占位（uploading），成功后换成 MinIO 公开读 URL */
const shots = ref<Array<{ path: string; uploading: boolean }>>([])
const form = ref({
  title: '',
  categoryId: '',
  grade: 'B',
  price: '',
  originalPrice: '',
  campusId: '',
  meetPointId: '',
  description: '',
  negotiable: false,
  delivery: false
})

/* ── 字典 ─────────────────────────────────────────────── */

/** 分类取二级挂在一级下：picker 显示「一级 / 二级」，值是二级 id */
const categoryLabels = ref<string[]>([])
const categoryIds = ref<string[]>([])
const grades = ref<Array<{ code: string; label: string }>>([])
const campusLabels = ref<string[]>([])
const campusIds = ref<string[]>([])
const meetPoints = ref<Array<{ id: string; name: string; campusId: string }>>([])

const meetPointLabels = computed(() =>
  meetPoints.value.filter((m) => m.campusId === form.value.campusId).map((m) => m.name)
)
const meetPointIds = computed(() =>
  meetPoints.value.filter((m) => m.campusId === form.value.campusId).map((m) => m.id)
)

onLoad(async (query) => {
  try {
    const dict = await userDictApi.all()
    flattenCategories(dict.categories)
    grades.value = dict.grades
    campusLabels.value = dict.campuses.map((c) => c.name)
    campusIds.value = dict.campuses.map((c) => c.id)
    meetPoints.value = dict.meetPoints
    // 默认选中第一项，避免空选导致必填拦不住
    if (categoryIds.value.length) form.value.categoryId = categoryIds.value[0]
    if (campusIds.value.length) form.value.campusId = campusIds.value[0]
  } catch {
    // request 层已提示
  }
  if (typeof query?.edit === 'string' && query.edit) await loadForEdit(query.edit)
})

/** 二级分类平铺成「一级 / 二级」；一级分类（parentId=0）只做分组头 */
function flattenCategories(categories: CategoryVO[]): void {
  const roots = categories.filter((c) => !c.parentId || c.parentId === '0')
  categoryLabels.value = []
  categoryIds.value = []
  for (const root of roots) {
    const children = categories.filter((c) => c.parentId === root.id)
    if (children.length === 0) {
      categoryLabels.value.push(root.name)
      categoryIds.value.push(root.id)
      continue
    }
    for (const child of children) {
      categoryLabels.value.push(`${root.name} / ${child.name}`)
      categoryIds.value.push(child.id)
    }
  }
}

/* ── 编辑模式 ─────────────────────────────────────────── */

const editMode = ref(false)
const editingId = ref('')
const editingDraft = ref(true)
/** 编辑时已有图片（真实 URL，提交原样带回） */
const existingImages = ref<string[]>([])

async function loadForEdit(id: string): Promise<void> {
  try {
    const item = await userItemApi.detail(id)
    editMode.value = true
    editingId.value = item.id
    editingDraft.value = item.status === 'DRAFT'
    uni.setNavigationBarTitle({ title: editingDraft.value ? '编辑草稿' : '编辑商品' })
    form.value.title = item.title
    form.value.categoryId = item.categoryId
    form.value.grade = item.grade
    form.value.price = item.price
    form.value.originalPrice = item.originPrice ?? ''
    form.value.campusId = item.campusId
    form.value.meetPointId = item.meetPointId ?? ''
    form.value.description = item.description ?? ''
    existingImages.value = (item.images ?? []).map((i) => i.url)
  } catch {
    // 详情拉取失败（不存在 / 非本人）时退回新建模式，request 层已提示
  }
}

/* ── 选图 ─────────────────────────────────────────────── */

function chooseImage(): void {
  uni.chooseMedia({
    count: MAX_SHOTS - shots.value.length,
    mediaType: ['image'],
    success: (res) => {
      // res.tempFiles 的类型在 @dcloudio/types 里较松，这里显式取路径与大小
      const files = (res as unknown as { tempFiles: { tempFilePath: string; size: number }[] }).tempFiles
      for (const f of files) {
        if (shots.value.length >= MAX_SHOTS) {
          uni.showToast({ title: `最多 ${MAX_SHOTS} 张图`, icon: 'none' })
          break
        }
        const path = f.tempFilePath
        shots.value.push({ path, uploading: true })
        void uploadImage(path, f.size)
          .then((url) => {
            const shot = shots.value.find((s) => s.path === path)
            if (shot) {
              shot.path = url
              shot.uploading = false
            }
          })
          .catch(() => {
            // 失败即移除占位；request 层已 toast 具体原因
            shots.value = shots.value.filter((s) => s.path !== path)
          })
      }
    }
  })
}

function removeShot(index: number): void {
  shots.value.splice(index, 1)
}

/* ── 校验与提交 ───────────────────────────────────────── */

/**
 * 必填校验（草稿与送审同一套），与后端 ItemPublishReq 注解对齐：
 * 标题 5–50 字、价格 > 0、分类、校区。成色 D 必须写描述（后端 Service 查，这里先拦）。
 */
const canSubmit = computed(() => {
  const title = form.value.title.trim()
  return (
    title.length >= 5 &&
    title.length <= 50 &&
    Number(form.value.price) > 0 &&
    !!form.value.categoryId &&
    !!form.value.campusId
  )
})

async function save(draft: boolean): Promise<void> {
  if (!canSubmit.value) {
    uni.showToast({ title: '标题 5–50 字，价格、分类、校区都要填', icon: 'none' })
    return
  }
  if (form.value.grade === 'D' && !form.value.description.trim()) {
    uni.showToast({ title: '成色为 D 时请描述写明具体瑕疵', icon: 'none' })
    return
  }
  // 图片未传完不提交：images 是「已上传 URL」列表
  if (shots.value.some((s) => s.uploading)) {
    uni.showToast({ title: '图片正在上传，稍候再提交', icon: 'none' })
    return
  }
  const req = {
    title: form.value.title.trim(),
    categoryId: form.value.categoryId,
    grade: form.value.grade,
    price: Number(form.value.price),
    originalPrice: form.value.originalPrice ? Number(form.value.originalPrice) : undefined,
    campusId: form.value.campusId,
    meetPointId: form.value.meetPointId || undefined,
    description: form.value.description.trim() || undefined,
    // 编辑时已有图片原样带回 + 新上传的 accessUrl，整体替换（后端不传差量）
    images: [...existingImages.value, ...shots.value.map((s) => s.path)],
    negotiable: form.value.negotiable ? 1 : 0,
    deliveryIncluded: form.value.delivery ? 1 : 0,
    draft
  }
  try {
    if (editMode.value) {
      await userItemApi.edit(editingId.value, req)
      uni.showToast({ title: draft ? '已存回草稿' : '已保存', icon: 'success' })
    } else {
      await userItemApi.publish(req)
      uni.showToast({
        title: draft ? '已存入草稿' : '已提交审核',
        icon: 'success'
      })
    }
    setTimeout(() => uni.navigateBack(), 800)
  } catch {
    // request 层已提示具体原因（1000 参数 / 1108 归属 / 1001 未登录跳登录）
  }
}

/** picker change：range 是标签数组，值按下标回查 id 数组 */
function onCategoryChange(e: { detail: { value: number } }): void {
  form.value.categoryId = categoryIds.value[e.detail.value] ?? ''
}

function onCampusChange(e: { detail: { value: number } }): void {
  form.value.campusId = campusIds.value[e.detail.value] ?? ''
  // 校区换了面交点作废：面交点挂在校区下，不跨校区选
  form.value.meetPointId = ''
}

function onMeetPointChange(e: { detail: { value: number } }): void {
  form.value.meetPointId = meetPointIds.value[e.detail.value] ?? ''
}

/**
 * switch 变更。事件类型在 @dcloudio/types 里是松的 Event，没有 detail，
 * 这里显式收形参再断言，避免 template 里到处 as any。
 */
function onToggle(field: 'negotiable' | 'delivery', e: unknown): void {
  form.value[field] = (e as { detail: { value: boolean } }).detail.value
}
</script>

<template>
  <view class="page cm-page cm-page--tab">
    <!-- 选图 -->
    <view class="sec">
      <text class="sec__t">图片（{{ shots.length }}/{{ MAX_SHOTS }}，第一张为封面）</text>
      <view class="shots">
        <view v-for="(s, i) in shots" :key="s.path" class="shot">
          <image v-if="!s.uploading" class="shot__img" :src="s.path" mode="aspectFill" />
          <view v-else class="shot__loading">上传中</view>
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
      <input v-model="form.title" class="input" placeholder="例如：iPad 9 64G WiFi 版 电池 92%" maxlength="50" />
    </view>

    <view class="sec">
      <text class="sec__t">分类</text>
      <picker :range="categoryLabels" @change="onCategoryChange">
        <view class="picker">{{ categoryLabels[categoryIds.indexOf(form.categoryId)] || '选择分类' }} ▾</view>
      </picker>
    </view>

    <view class="sec">
      <text class="sec__t">成色</text>
      <view class="grades">
        <text
          v-for="g in grades"
          :key="g.code"
          class="grade"
          :class="{ 'grade--on': form.grade === g.code }"
          @click="form.grade = g.code"
        >
          {{ g.code }} {{ g.label }}
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
      <picker :range="campusLabels" @change="onCampusChange">
        <view class="picker">{{ campusLabels[campusIds.indexOf(form.campusId)] || '选择校区' }} ▾</view>
      </picker>
      <picker :range="meetPointLabels" @change="onMeetPointChange">
        <view class="picker">
          {{ meetPointLabels[meetPointIds.indexOf(form.meetPointId)] || (form.campusId ? '选择面交点（选填）' : '先选校区') }} ▾
        </view>
      </picker>
    </view>

    <view class="sec">
      <text class="sec__t">描述</text>
      <textarea v-model="form.description" class="textarea" placeholder="成色细节、购买渠道、瑕疵说明…" />
    </view>

    <view class="sec">
      <view class="switch-row">
        <text>可议价</text>
        <switch :checked="form.negotiable" color="#FF6A00" @change="onToggle('negotiable', $event)" />
      </view>
      <view class="switch-row">
        <text>包校园配送</text>
        <switch :checked="form.delivery" color="#FF6A00" @change="onToggle('delivery', $event)" />
      </view>
    </view>

    <!-- 提交：草稿态 / 新建两个按钮；已发布商品只有「保存」 -->
    <template v-if="editMode && !editingDraft">
      <view class="submit" hover-class="submit--active" :class="{ 'submit--off': !canSubmit }" @click="save(false)">
        <text class="submit__t">保存</text>
      </view>
    </template>
    <template v-else>
      <view class="btns">
        <view class="btn btn--ghost" hover-class="submit--active" :class="{ 'btn--off': !canSubmit }" @click="save(true)">
          <text class="btn__t">存草稿</text>
        </view>
        <view class="btn" hover-class="submit--active" :class="{ 'submit--off': !canSubmit }" @click="save(false)">
          <text class="submit__t">提交送审</text>
        </view>
      </view>
    </template>

    <TabBar active="publish" />
  </view>
</template>

<style scoped lang="scss">
.page {
  /* 底部导航由 TabBar 占位，这里只留卡片自身的呼吸 */
  padding: $cm-space-4 $cm-space-4 $cm-space-5;
}

.sec {
  margin-bottom: $cm-space-4;
  padding: $cm-space-5;
  background: $cm-color-surface;
  border-radius: $cm-radius-2xl;
  box-shadow: $cm-shadow-xs;
}

.sec__t {
  display: block;
  margin-bottom: $cm-space-4;
  font-size: $cm-font-size-base;
  font-weight: $cm-font-weight-heavy;
  color: $cm-color-text-1;
}

/* ── 选图 ───────────────────────────────────────────────── */

.shots {
  display: flex;
  flex-wrap: wrap;
  gap: $cm-space-3;
}

.shot {
  position: relative;
  width: 168rpx;
  height: 168rpx;
  border-radius: $cm-radius-lg;
  overflow: hidden;
}

.shot__img {
  width: 100%;
  height: 100%;
}

/* 上传占位：票据已签、文件在传 */
.shot__loading {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $cm-font-size-xs;
  color: $cm-color-text-3;
  background: $cm-color-bg;
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
  height: 92rpx;
  line-height: 92rpx;
  text-align: center;
  border-radius: $cm-radius-pill;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: $cm-shadow-brand;
  transition: transform 140ms ease-out;
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

/* 双按钮：草稿在左（次要），送审在右（主要） */
.btns {
  display: flex;
  gap: $cm-space-3;
}

.btn {
  flex: 1;
  height: 92rpx;
  line-height: 92rpx;
  text-align: center;
  border-radius: $cm-radius-pill;
  background-image: linear-gradient(135deg, $cm-color-brand-glow, $cm-color-brand);
  box-shadow: $cm-shadow-brand;
  transition: transform 140ms ease-out;
}

.btn--ghost {
  background: $cm-color-surface;
  border: 1rpx solid $cm-color-brand;
}

.btn__t {
  font-size: $cm-font-size-md;
  font-weight: 600;
  color: $cm-color-brand-text;
}

.btn--off {
  background: $cm-color-brand-bg;
  border-color: transparent;
}

.btn--off .btn__t {
  color: $cm-color-text-3;
}
</style>

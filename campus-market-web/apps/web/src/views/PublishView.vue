<script setup lang="ts">
/**
 * P3 发布闲置（桌面独占重头戏，business/01 §三 F-W3）
 *
 * <p>桌面价值就体现在这一页：**拖拽多图 + 图序调整 + 右侧实时预览 + Ctrl+Enter 提交**。
 *
 * <p>两种提交意图共用一个表单：**存草稿**（`draft=true`）与**提交送审**（`draft=false`）。
 * 必填字段（标题 5–50 字 / 价格 / 分类 / 成色 / 校区）两种意图都查——`item` 表这些列
 * NOT NULL，草稿同样要落库；草稿豁免的是图片与描述。编辑模式（`?edit={id}`）下
 * 草稿商品显示「存草稿 + 提交送审」，已发布商品只有「保存」。
 *
 * <p>图片两步票据制上传（`uploadImage`，tech/01 4.4 策略模式）：先占位「上传中」，
 * 成功后换成 MinIO 公开读 URL；提交时 images = 已有图片 + 新上传 URL 整体替换。
 */

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ITEM_STATUS,
  type ConditionGrade,
  type GoodsItem,
  type ItemStatus
} from '@campus/shared'
import {
  userDictApi,
  userItemApi,
  userAddressApi,
  uploadImage,
  ADDRESS_KIND,
  fullAddress,
  type CategoryVO,
  type UserAddressVO
} from '@campus/api'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()

/** 收货方式（与后端 DeliveryTypeEnum 的 code 一致）：1 面交 2 自提 3 送货上门 */
const DELIVERY_TYPES = [
  { code: 1, label: '面交', hint: '约定面交点，双方现场交易' },
  { code: 2, label: '自提', hint: '买家到你设置的提货地址自取' },
  { code: 3, label: '送货上门', hint: '买家下单时填收货地址，你负责送达' }
] as const

/** 卖家自己的自提地址（kind=2），只在收货方式选「自提」时需要 */
const pickupAddresses = ref<UserAddressVO[]>([])

/* ── 字典：分类 / 成色 / 校区 / 面交点 ─────────────────── */

const categories = ref<CategoryVO[]>([])
const grades = ref<Array<{ code: string; label: string }>>([])
const campuses = ref<Array<{ id: string; name: string }>>([])
const meetPoints = ref<Array<{ id: string; name: string; campusId: string }>>([])

/** 分类平铺带回 parentId：一级做分组头，二级挂进去；0 是一级 */
const categoryGroups = computed(() => {
  const roots = categories.value.filter((c) => !c.parentId || c.parentId === '0')
  return roots.map((root) => ({
    root,
    children: categories.value.filter((c) => c.parentId === root.id)
  }))
})

/** 面交点按所选校区过滤：选完校区才出现可选面交点 */
const meetPointsOfCampus = computed(() =>
  meetPoints.value.filter((m) => m.campusId === form.value.campusId)
)

const categoryName = computed(
  () => categories.value.find((c) => c.id === form.value.categoryId)?.name ?? ''
)

onMounted(async () => {
  try {
    const dict = await userDictApi.all()
    categories.value = dict.categories
    grades.value = dict.grades.map((g) => ({ code: g.code, label: g.label }))
    campuses.value = dict.campuses
    meetPoints.value = dict.meetPoints
  } catch {
    // http 层已提示；字典拉不到时下拉为空，提交会被必填校验拦住
  }
})

/** 自提地址簿：选「自提」时从这里挑一个作为提货点。未登录 / 拉取失败时留空，前端提示去添加 */
onMounted(async () => {
  try {
    pickupAddresses.value = await userAddressApi.list(ADDRESS_KIND.PICKUP)
  } catch {
    // 无地址不影响发布面交 / 送货上门；选「自提」时会被必填校验拦下并提示先添加
  }
})

/* ── 编辑模式：?edit={id} 回填已有商品 ─────────────────── */

const editMode = ref(false)
const editingId = ref('')
const editingStatus = ref<ItemStatus>('DRAFT')
/** 编辑时已有的图片（真实 URL，提交时与新上传的一并整体替换） */
const existingImages = ref<string[]>([])

onMounted(async () => {
  const id = router.currentRoute.value.query.edit
  if (typeof id !== 'string' || !id) return
  try {
    const item = await userItemApi.detail(id)
    editMode.value = true
    editingId.value = item.id
    editingStatus.value = item.status
    form.value.title = item.title
    form.value.categoryId = item.categoryId
    form.value.grade = item.grade as ConditionGrade
    form.value.price = Number(item.price)
    form.value.originPrice = item.originPrice ? Number(item.originPrice) : null
    form.value.campusId = item.campusId
    form.value.meetPointId = item.meetPointId ?? ''
    form.value.deliveryType = item.deliveryType ?? 1
    form.value.pickupAddressId = item.pickupAddressId ?? ''
    form.value.description = item.description ?? ''
    existingImages.value = (item.images ?? []).map((i) => i.url)
  } catch {
    // 详情拉取失败（不存在 / 非本人）时退回新建模式，http 层已提示
  }
})

/** 编辑的是草稿：两个按钮都显示；已发布商品只有「保存」 */
const editingDraft = computed(() => editingStatus.value === 'DRAFT')

/* ── 图片：拖拽 + 排序 ─────────────────────────────────── */

interface Shot {
  id: string
  url: string
  /** 上传中：占位期间不允许提交，成功后落真实 accessUrl */
  uploading: boolean
}

const shots = ref<Shot[]>([])
const dragging = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
let seed = 0

function addFiles(files: FileList | null): void {
  if (!files?.length) return
  for (const f of Array.from(files)) {
    if (!f.type.startsWith('image/')) continue
    // 逐张查上限：上传是异步的，批量超出时先到先得
    if (shots.value.length >= 9) {
      ElMessage.warning({ message: '最多 9 张图', duration: 2000 })
      break
    }
    const id = `s${seed++}`
    // 先占位再上传：失败即移除，成功把 blob 占位换成真实 accessUrl
    shots.value.push({ id, url: '', uploading: true })
    void uploadImage(f)
      .then((url) => {
        const shot = shots.value.find((s) => s.id === id)
        if (shot) {
          shot.url = url
          shot.uploading = false
        }
      })
      .catch((e: unknown) => {
        shots.value = shots.value.filter((s) => s.id !== id)
        ElMessage.warning({ message: e instanceof Error ? e.message : '图片上传失败', duration: 2000 })
      })
  }
}

function onDrop(e: DragEvent): void {
  dragging.value = false
  addFiles(e.dataTransfer?.files ?? null)
}

function removeShot(id: string): void {
  const target = shots.value.find((s) => s.id === id)
  // 只回收 blob: 预览地址；真实 URL 由 MinIO 管，revoke 它无意义
  if (target?.url.startsWith('blob:')) URL.revokeObjectURL(target.url)
  shots.value = shots.value.filter((s) => s.id !== id)
}

/** 图序调整：左移 / 右移按钮——骨架期不做拖拽排序，先把「顺序可调」这件事做实 */
function moveShot(index: number, delta: number): void {
  const next = index + delta
  if (next < 0 || next >= shots.value.length) return
  const arr = [...shots.value]
  ;[arr[index], arr[next]] = [arr[next], arr[index]]
  shots.value = arr
}

/** 组件卸载时回收未拆穿的 blob 预览，否则刷新前的内存白占着 */
onBeforeUnmount(() => {
  for (const s of shots.value) {
    if (s.url.startsWith('blob:')) URL.revokeObjectURL(s.url)
  }
})

/* ── 表单 ───────────────────────────────────────────────── */

const form = ref({
  title: '',
  categoryId: '',
  grade: 'B' as ConditionGrade,
  price: null as number | null,
  originPrice: null as number | null,
  campusId: '',
  meetPointId: '',
  deliveryType: 1 as number,
  pickupAddressId: '',
  description: '',
  negotiable: false
})

/** 实时预览：把表单拼成一个 GoodsItem 喂给 ProductCard，预览与实际卡片永远一致 */
const preview = computed<GoodsItem>(() => ({
  id: 'preview',
  title: form.value.title || '标题预览（填入标题后显示）',
  categoryId: form.value.categoryId,
  category: categoryName.value || '其他闲置',
  price: form.value.price ?? 0,
  originalPrice: form.value.originPrice ?? undefined,
  condition: form.value.grade,
  campus: campuses.value.find((c) => c.id === form.value.campusId)?.name ?? '待选校区',
  meetPoint: meetPoints.value.find((m) => m.id === form.value.meetPointId)?.name ?? '待填面交点',
  negotiable: form.value.negotiable,
  delivery: form.value.deliveryType === 3,
  status: ITEM_STATUS.DRAFT,
  wantCount: 0,
  seller: { id: 'me', nick: '我', verified: true, credit: '良好', tints: ['#BFD4EA', '#8FA9C4'] },
  publishedAt: new Date().toISOString(),
  description: form.value.description,
  images: [],
  tints: ['#ECEFF3', '#D3D9E0']
}))

/**
 * 必填校验（草稿与送审同一套）：标题 5–50 字、价格 > 0、分类 / 成色 / 校区。
 * 与后端 `ItemPublishReq` 的注解逐条对齐——后端是最终判官，前端先拦一遍只为少一次往返。
 */
const canSubmit = computed(
  () =>
    form.value.title.trim().length >= 5 &&
    form.value.title.trim().length <= 50 &&
    (form.value.price ?? 0) > 0 &&
    !!form.value.categoryId &&
    !!form.value.campusId &&
    // 自提必须选一个提货地址（后端硬校验，前端先拦一遍）
    (form.value.deliveryType !== 2 || !!form.value.pickupAddressId)
)

/** Ctrl+Enter 提交（design/01 §七 键盘约定） */
function onKeydown(e: KeyboardEvent): void {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    void save(false)
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

/**
 * 提交 / 存草稿。draft=true 落草稿不进队；draft=false 送审（草稿商品必进队）。
 * 成色 D 必须写描述：跨字段规则后端查（Service），这里先拦一遍同样口径。
 */
async function save(draft: boolean): Promise<void> {
  if (!canSubmit.value) {
    ElMessage.warning({ message: '标题 5–50 字，价格、分类、校区都要填', duration: 2000 })
    return
  }
  if (form.value.grade === 'D' && !form.value.description.trim()) {
    ElMessage.warning({ message: '成色为「明显使用痕迹」时，请描述写明具体瑕疵', duration: 2000 })
    return
  }
  // 图片未传完不提交：images 是「已上传 URL」列表，半张图进去后端也落不了库
  if (shots.value.some((s) => s.uploading)) {
    ElMessage.warning({ message: '图片正在上传，稍候再提交', duration: 2000 })
    return
  }
  const req = {
    title: form.value.title.trim(),
    categoryId: form.value.categoryId,
    grade: form.value.grade,
    price: form.value.price as number,
    originPrice: form.value.originPrice ?? undefined,
    campusId: form.value.campusId,
    // 面交点只对面交有意义；自提 / 送货上门带过去也会被后端忽略，这里干脆不发
    meetPointId: form.value.deliveryType === 1 ? form.value.meetPointId || undefined : undefined,
    deliveryType: form.value.deliveryType,
    pickupAddressId: form.value.deliveryType === 2 ? form.value.pickupAddressId || undefined : undefined,
    description: form.value.description.trim() || undefined,
    // 编辑时已有图片原样带回 + 新上传的 accessUrl，整体替换（后端不传差量）
    images: [...existingImages.value, ...shots.value.map((s) => s.url)],
    negotiable: form.value.negotiable ? 1 : 0,
    draft
  }
  try {
    if (editMode.value) {
      await userItemApi.edit(editingId.value, req)
      ElMessage.success({ message: draft ? '已存回草稿' : '已保存', duration: 2000 })
    } else {
      await userItemApi.publish(req)
      ElMessage.success({
        message: draft ? '已存入草稿' : '已提交审核，通常 2 小时内完成',
        duration: 2000
      })
    }
    void router.push({ name: 'my-items' })
  } catch {
    // http 层已提示具体原因（1000 参数 / 1108 归属 / 1001 未登录跳转）
  }
}
</script>

<template>
  <div class="cm-shell publish">
    <div class="cols">
      <!-- ── 左：图片 + 表单 ────────────────────────────── -->
      <section class="pane">
        <h2 class="pane__title">1 · 上传图片（拖拽或点击，最多 9 张）</h2>

        <div
          class="drop"
          :class="{ 'drop--over': dragging }"
          @dragover.prevent="dragging = true"
          @dragleave="dragging = false"
          @drop.prevent="onDrop"
          @click="fileInput?.click()"
        >
          <p class="drop__main">把图片拖到这里</p>
          <p class="drop__sub">支持 JPG / PNG，第一张作封面</p>
          <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="addFiles(($event.target as HTMLInputElement).files)" />
        </div>

        <div v-if="shots.length" class="shots">
          <div v-for="(s, i) in shots" :key="s.id" class="shot">
            <img v-if="s.url" :src="s.url" alt="" />
            <div v-else class="shot__uploading">上传中…</div>
            <div class="shot__bar">
              <button type="button" aria-label="前移" @click.stop="moveShot(i, -1)">←</button>
              <button type="button" aria-label="后移" @click.stop="moveShot(i, 1)">→</button>
              <button type="button" aria-label="删除" @click.stop="removeShot(s.id)">×</button>
            </div>
            <span v-if="i === 0" class="shot__cover">封面</span>
          </div>
        </div>

        <h2 class="pane__title">2 · 填写信息</h2>

        <el-form label-width="88px" class="form">
          <el-form-item label="标题">
            <el-input v-model="form.title" maxlength="50" show-word-limit placeholder="例如：iPad 9 64G WiFi 版 电池 92%" />
          </el-form-item>

          <el-form-item label="分类">
            <el-select v-model="form.categoryId" placeholder="选择分类">
              <el-option-group v-for="g in categoryGroups" :key="g.root.id" :label="g.root.name">
                <el-option v-for="c in g.children" :key="c.id" :label="c.name" :value="c.id" />
              </el-option-group>
            </el-select>
          </el-form-item>

          <el-form-item label="成色">
            <el-radio-group v-model="form.grade">
              <el-radio v-for="g in grades" :key="g.code" :value="g.code">
                {{ g.code }} · {{ g.label }}
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="价格">
            <div class="price-row">
              <el-input-number v-model="form.price" :min="0" :step="5" placeholder="现价" />
              <span class="price-row__label">原价（选填）</span>
              <el-input-number v-model="form.originPrice" :min="0" :step="5" placeholder="原价" />
            </div>
          </el-form-item>

          <el-form-item label="校区">
            <el-select v-model="form.campusId" placeholder="选择校区" @change="form.meetPointId = ''">
              <el-option v-for="c in campuses" :key="c.id" :label="c.name" :value="c.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="收货方式">
            <div class="delivery-pick">
              <el-radio-group v-model="form.deliveryType">
                <el-radio v-for="d in DELIVERY_TYPES" :key="d.code" :value="d.code">{{ d.label }}</el-radio>
              </el-radio-group>
              <p class="delivery-pick__hint">
                {{ DELIVERY_TYPES.find((d) => d.code === form.deliveryType)?.hint }}
              </p>
            </div>
          </el-form-item>

          <el-form-item v-if="form.deliveryType === 1" label="面交点">
            <el-select v-model="form.meetPointId" placeholder="选择面交点" :disabled="!form.campusId">
              <el-option v-for="m in meetPointsOfCampus" :key="m.id" :label="m.name" :value="m.id" />
            </el-select>
          </el-form-item>

          <el-form-item v-if="form.deliveryType === 2" label="提货地址">
            <div class="pickup-pick">
              <el-select
                v-model="form.pickupAddressId"
                placeholder="选择自提地址"
                :disabled="!pickupAddresses.length"
              >
                <el-option
                  v-for="a in pickupAddresses"
                  :key="a.id"
                  :label="`${a.contactName} · ${fullAddress(a)}`"
                  :value="a.id"
                />
              </el-select>
              <router-link v-if="!pickupAddresses.length" class="pickup-pick__todo" to="/address?kind=2">
                还没有自提地址，去添加 ›
              </router-link>
            </div>
          </el-form-item>

          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="4" placeholder="成色细节、购买渠道、瑕疵说明…" />
          </el-form-item>

          <el-form-item label="交易选项">
            <el-checkbox v-model="form.negotiable">可议价</el-checkbox>
          </el-form-item>
        </el-form>

        <div class="submit">
          <template v-if="editMode && !editingDraft">
            <el-button type="primary" size="large" :disabled="!canSubmit" @click="save(false)">保存</el-button>
            <span class="tip">或按 Ctrl + Enter</span>
          </template>
          <template v-else>
            <el-button type="primary" size="large" :disabled="!canSubmit" @click="save(false)">
              {{ editMode ? '提交送审' : '发布，送审核' }}
            </el-button>
            <el-button size="large" :disabled="!canSubmit" @click="save(true)">存草稿</el-button>
            <span class="tip">或按 Ctrl + Enter</span>
          </template>
        </div>
      </section>

      <!-- ── 右：实时预览 ────────────────────────────────── -->
      <aside class="pane pane--preview">
        <h2 class="pane__title">实时预览</h2>
        <p class="pane__hint">这是商品在列表页里的真实样子</p>
        <div class="preview-card">
          <ProductCard :item="preview" :preview-src="shots[0]?.url || undefined" />
        </div>

        <ul class="checklist">
          <li :class="{ ok: form.title.trim().length >= 5 && form.title.trim().length <= 50 }">标题 5–50 字</li>
          <li :class="{ ok: (form.price ?? 0) > 0 }">已填价格</li>
          <li :class="{ ok: !!form.categoryId }">已选分类</li>
          <li :class="{ ok: !!form.campusId }">已选校区</li>
          <li v-if="form.deliveryType === 2" :class="{ ok: !!form.pickupAddressId }">已选自提地址</li>
          <li :class="{ ok: form.grade !== 'D' || !!form.description.trim() }">成色 D 已写瑕疵</li>
        </ul>
      </aside>
    </div>
  </div>
</template>

<style scoped lang="scss">
.publish {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-4);
  padding-top: var(--cm-space-4);
}

.cols {
  display: flex;
  gap: var(--cm-space-4);
  align-items: flex-start;
}

.pane {
  flex: 1;
  min-width: 0;
  padding: var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-1);
}

.pane--preview {
  flex: none;
  width: 300px;
  position: sticky;
  /* 顶栏分类条取消后，吸附点只留 nav 高度 */
  top: calc(var(--cm-layout-nav-h) + var(--cm-space-4));
}

.pane__title {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-md);
  font-weight: 600;
}

.pane__hint {
  margin: 0 0 var(--cm-space-3);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* ── 拖拽区 ─────────────────────────────────────────────── */

.drop {
  border: 2px dashed var(--cm-color-border-strong);
  border-radius: var(--cm-radius-lg);
  padding: var(--cm-space-8) var(--cm-space-4);
  text-align: center;
  cursor: pointer;
  transition: border-color 180ms ease-out, background-color 180ms ease-out;
}

.drop:hover,
.drop--over {
  border-color: var(--cm-color-brand);
  background: var(--cm-color-brand-bg);
}

.drop__main {
  margin: 0;
  font-size: var(--cm-font-size-md);
  font-weight: 600;
}

.drop__sub {
  margin: var(--cm-space-1) 0 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.shots {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(96px, 1fr));
  gap: var(--cm-space-2);
  margin-top: var(--cm-space-3);
}

.shot {
  position: relative;
  aspect-ratio: 1;
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-md);
  overflow: hidden;
}

.shot img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 上传占位：票据已签、文件在传，图未就位 */
.shot__uploading {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--cm-font-size-xs, 12px);
  color: var(--cm-color-text-3);
  background: var(--cm-color-bg);
}

.shot__bar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  gap: var(--cm-space-1);
  padding: 2px;
  background: rgba(31, 31, 38, 0.72);
}

.shot__bar button {
  width: 20px;
  height: 20px;
  font-size: var(--cm-font-size-xs);
  color: #fff;
  background: none;
  border: none;
  cursor: pointer;
}

.shot__cover {
  position: absolute;
  left: var(--cm-space-1);
  top: var(--cm-space-1);
  font-size: var(--cm-font-size-xs);
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
  border-radius: var(--cm-radius-sm);
  padding: 0 4px;
}

/* ── 表单 ───────────────────────────────────────────────── */

.form {
  margin-top: var(--cm-space-2);
}

.price-row {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
}

.price-row__label {
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

/* 收货方式：单选 + 一句说明，说明随选择变化，减少「选了不知道意味着什么」的困惑 */
.delivery-pick__hint {
  margin: var(--cm-space-1) 0 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.pickup-pick {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-1);
  width: 100%;
}

.pickup-pick__todo {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-brand);
  text-decoration: none;
}

.pickup-pick__todo:hover {
  text-decoration: underline;
}

.submit {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  margin-top: var(--cm-space-5);
  padding-top: var(--cm-space-4);
  border-top: 1px solid var(--cm-color-border);
}

.tip {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

/* ── 预览 ───────────────────────────────────────────────── */

.preview-card {
  border: 1px dashed var(--cm-color-border-strong);
  border-radius: var(--cm-radius-lg);
  padding: var(--cm-space-2);
}

/* 预览里的卡片不该能点进详情 */
.preview-card :deep(.card) {
  cursor: default;
  pointer-events: none;
}

.checklist {
  list-style: none;
  margin: var(--cm-space-4) 0 0;
  padding: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.checklist li::before {
  content: '○ ';
}

.checklist li.ok {
  color: var(--cm-color-ok);
}

.checklist li.ok::before {
  content: '● ';
}

@media (max-width: 1023px) {
  .cols {
    flex-direction: column;
  }

  .pane--preview {
    width: 100%;
    position: static;
  }
}
</style>

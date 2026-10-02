<script setup lang="ts">
/**
 * P3 发布闲置（桌面独占重头戏，business/01 §三 F-W3）
 *
 * <p>桌面价值就体现在这一页：**拖拽多图 + 图序调整 + 右侧实时预览 + Ctrl+Enter 提交**。
 * 四项缺一项，Web 端就退化成移动端的放大版。
 *
 * <p>骨架期说明：图片只做本地预览（`URL.createObjectURL`），不上传——
 * 上传要走 MinIO 直传（tech/02 §6.5），后端接口未实现。提交也是本地反馈，不落库。
 */

import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  CAMPUSES,
  CATEGORIES,
  CONDITION_GRADES,
  CONDITION_LABELS,
  ITEM_STATUS,
  type ConditionGrade,
  type GoodsItem
} from '@campus/shared'
import { ElMessage } from 'element-plus'
import ProductCard from '@/components/ProductCard.vue'

const router = useRouter()

/* ── 图片：拖拽 + 排序 ──────────────────────────────────── */

interface Shot {
  id: string
  url: string
}

const shots = ref<Shot[]>([])
const dragging = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
let seed = 0

function addFiles(files: FileList | null): void {
  if (!files?.length) return
  for (const f of Array.from(files)) {
    if (!f.type.startsWith('image/')) continue
    shots.value.push({ id: `s${seed++}`, url: URL.createObjectURL(f) })
  }
  if (shots.value.length > 9) {
    // design/01：发布最多 9 张（预览卡片是 9 宫格）
    shots.value = shots.value.slice(0, 9)
    ElMessage.warning({ message: '最多 9 张图', duration: 2000 })
  }
}

function onDrop(e: DragEvent): void {
  dragging.value = false
  addFiles(e.dataTransfer?.files ?? null)
}

function removeShot(id: string): void {
  const target = shots.value.find((s) => s.id === id)
  if (target) URL.revokeObjectURL(target.url)
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

/** 组件卸载时回收 objectURL，否则刷新前的内存白占着 */
onBeforeUnmount(() => {
  for (const s of shots.value) URL.revokeObjectURL(s.url)
})

/* ── 表单 ───────────────────────────────────────────────── */

const form = ref({
  title: '',
  category: CATEGORIES[1],
  condition: 'B' as ConditionGrade,
  price: null as number | null,
  originalPrice: null as number | null,
  campus: CAMPUSES[0],
  meetPoint: '',
  description: '',
  negotiable: false,
  delivery: false
})

/** 实时预览：把表单拼成一个 GoodsItem 喂给 ProductCard，预览与实际卡片永远一致 */
const preview = computed<GoodsItem>(() => ({
  id: 'preview',
  title: form.value.title || '标题预览（填入标题后显示）',
  category: form.value.category,
  price: form.value.price ?? 0,
  originalPrice: form.value.originalPrice ?? undefined,
  condition: form.value.condition,
  campus: form.value.campus,
  meetPoint: form.value.meetPoint || '待填面交点',
  negotiable: form.value.negotiable,
  delivery: form.value.delivery,
  status: ITEM_STATUS.DRAFT,
  wantCount: 0,
  seller: { id: 'me', nick: '我', verified: true, credit: '良好', tints: ['#BFD4EA', '#8FA9C4'] },
  publishedAt: new Date().toISOString(),
  description: form.value.description,
  images: [],
  tints: ['#ECEFF3', '#D3D9E0']
}))

const canSubmit = computed(() => form.value.title.trim().length >= 4 && (form.value.price ?? 0) > 0)

/** Ctrl+Enter 提交（design/01 §七 键盘约定） */
function onKeydown(e: KeyboardEvent): void {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
    e.preventDefault()
    submit()
  }
}

onMounted(() => window.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))

function submit(): void {
  if (!canSubmit.value) {
    ElMessage.warning({ message: '标题至少 4 个字，价格要大于 0', duration: 2000 })
    return
  }
  // 骨架期：接口未实现，只给反馈。真实实现走 POST /api/item/publish
  ElMessage.success({ message: '已提交审核（骨架期未落库）', duration: 2000 })
  void router.push({ name: 'home' })
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
          @dragleave.prevent="dragging = false"
          @drop.prevent="onDrop"
          @click="fileInput?.click()"
        >
          <p class="drop__main">把图片拖到这里</p>
          <p class="drop__sub">支持 JPG / PNG，第一张作封面</p>
          <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="addFiles(($event.target as HTMLInputElement).files)" />
        </div>

        <div v-if="shots.length" class="shots">
          <div v-for="(s, i) in shots" :key="s.id" class="shot">
            <img :src="s.url" alt="" />
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
            <el-input v-model="form.title" maxlength="40" show-word-limit placeholder="例如：iPad 9 64G WiFi 版 电池 92%" />
          </el-form-item>

          <el-form-item label="分类">
            <el-select v-model="form.category">
              <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>

          <el-form-item label="成色">
            <el-radio-group v-model="form.condition">
              <el-radio v-for="g in CONDITION_GRADES" :key="g" :value="g">
                {{ g }} · {{ CONDITION_LABELS[g] }}
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="价格">
            <div class="price-row">
              <el-input-number v-model="form.price" :min="0" :step="5" placeholder="现价" />
              <span class="price-row__label">原价（选填）</span>
              <el-input-number v-model="form.originalPrice" :min="0" :step="5" placeholder="原价" />
            </div>
          </el-form-item>

          <el-form-item label="校区">
            <el-select v-model="form.campus">
              <el-option v-for="c in CAMPUSES" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>

          <el-form-item label="面交点">
            <el-input v-model="form.meetPoint" placeholder="例如：图书馆东门" />
          </el-form-item>

          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="4" placeholder="成色细节、购买渠道、瑕疵说明…" />
          </el-form-item>

          <el-form-item label="交易选项">
            <el-checkbox v-model="form.negotiable">可议价</el-checkbox>
            <el-checkbox v-model="form.delivery">包校园配送</el-checkbox>
          </el-form-item>
        </el-form>

        <div class="submit">
          <el-button type="primary" size="large" :disabled="!canSubmit" @click="submit">提交送审</el-button>
          <span class="tip">或按 Ctrl + Enter</span>
        </div>
      </section>

      <!-- ── 右：实时预览 ────────────────────────────────── -->
      <aside class="pane pane--preview">
        <h2 class="pane__title">实时预览</h2>
        <p class="pane__hint">这是商品在列表页里的真实样子</p>
        <div class="preview-card">
          <ProductCard :item="preview" :preview-src="shots[0]?.url" />
        </div>

        <ul class="checklist">
          <li :class="{ ok: form.title.trim().length >= 4 }">标题 ≥ 4 字</li>
          <li :class="{ ok: (form.price ?? 0) > 0 }">已填价格</li>
          <li :class="{ ok: shots.length > 0 }">至少 1 张图</li>
          <li :class="{ ok: !!form.meetPoint }">已填面交点</li>
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

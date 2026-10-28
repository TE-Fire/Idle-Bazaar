<script setup lang="ts">
/**
 * 发布动态弹层（notes/22 S3）
 *
 * <p>只发 `type=1` 纯状态：文字 + 最多 9 图。分享商品走商品详情页的「分享到广场」，
 * 那里 itemId 天然在手，不必让用户在弹层里再挑一次商品。
 *
 * <p>图片沿用发布闲置的两步票据制上传（`uploadImage`）：先占位显示「上传中」，
 * 上传成功才把 URL 塞进可提交列表；有图还在传时禁止提交（否则会丢掉那张图）。
 *
 * <p>正文是否含敏感词由服务端判定：高危词整条被拒（4024，http 层已提示），
 * 低危词服务端替换成 `*` 后入库，所以提交成功后要按服务端返回内容刷新列表。
 */

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadImage, userMomentApi } from '@campus/api'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'published', id: string): void
}>()

const MAX_IMAGES = 9
const MAX_LEN = 500

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v)
})

interface Shot {
  id: number
  url: string
  uploading: boolean
}

const content = ref('')
const shots = ref<Shot[]>([])
const submitting = ref(false)
const fileRef = ref<HTMLInputElement | null>(null)
let seq = 0

const canAdd = computed(() => shots.value.length < MAX_IMAGES)
const uploading = computed(() => shots.value.some((s) => s.uploading))
const canSubmit = computed(
  () => !submitting.value && !uploading.value && (!!content.value.trim() || shots.value.some((s) => s.url))
)

function openPicker(): void {
  if (!canAdd.value) {
    ElMessage.info(`最多 ${MAX_IMAGES} 张图`)
    return
  }
  fileRef.value?.click()
}

async function onFiles(e: Event): Promise<void> {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files ?? []).slice(0, MAX_IMAGES - shots.value.length)
  input.value = ''
  for (const f of files) {
    const shot: Shot = { id: ++seq, url: '', uploading: true }
    shots.value = [...shots.value, shot]
    try {
      const url = await uploadImage(f)
      const hit = shots.value.find((s) => s.id === shot.id)
      if (hit) {
        hit.url = url
        hit.uploading = false
      }
    } catch (err) {
      // 一张失败不牵连其他：移除占位并提示
      shots.value = shots.value.filter((s) => s.id !== shot.id)
      ElMessage.error(err instanceof Error ? err.message : '图片上传失败')
    }
  }
}

function removeShot(id: number): void {
  shots.value = shots.value.filter((s) => s.id !== id)
}

function reset(): void {
  content.value = ''
  shots.value = []
  submitting.value = false
}

function close(): void {
  visible.value = false
  reset()
}

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const id = await userMomentApi.publish({
      type: 1,
      content: content.value.trim() || undefined,
      images: shots.value.map((s) => s.url).filter(Boolean)
    })
    ElMessage.success('已发布')
    emit('published', id)
    close()
  } catch {
    // http 层已提示（含 4024 敏感词拒绝），保留草稿让用户改
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="发条动态" width="560px" :close-on-click-modal="false" @closed="reset">
    <div class="composer">
      <el-input
        v-model="content"
        type="textarea"
        :rows="5"
        :maxlength="MAX_LEN"
        show-word-limit
        resize="none"
        placeholder="分享你的校园生活、闲置心得…"
      />

      <!-- 图片格：空位点击选图，上传中显示遮罩，右上角小 × 删除 -->
      <div class="shots">
        <div v-for="s in shots" :key="s.id" class="shot">
          <img v-if="s.url" :src="s.url" alt="" />
          <div v-else class="shot__waitting">上传中…</div>
          <button type="button" class="shot__del" @click="removeShot(s.id)">×</button>
        </div>
        <button v-if="canAdd" type="button" class="shot shot--add" @click="openPicker">
          <span class="shot__plus">＋</span>
          <span class="shot__tip">{{ shots.length }}/{{ MAX_IMAGES }}</span>
        </button>
      </div>
      <input ref="fileRef" type="file" accept="image/jpeg,image/png,image/webp" multiple hidden @change="onFiles" />

      <p class="tips">发布即表示你同意校园文明公约；违规内容将被过滤或拒收。</p>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">发布</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.composer {
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-3);
}

.shots {
  display: flex;
  flex-wrap: wrap;
  gap: var(--cm-space-2);
}

.shot {
  position: relative;
  width: 84px;
  height: 84px;
  padding: 0;
  overflow: hidden;
  background: var(--cm-color-bg);
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-sm);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  &--add {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    color: var(--cm-color-text-3);
    background: var(--cm-color-surface);
    border-style: dashed;
    cursor: pointer;

    &:hover {
      border-color: var(--cm-color-text-2);
      color: var(--cm-color-text-2);
    }
  }
}

.shot__waitting {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.shot__del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 18px;
  height: 18px;
  padding: 0;
  font-size: 13px;
  line-height: 1;
  color: #fff;
  background: rgba(0, 0, 0, 0.5);
  border: none;
  border-radius: 50%;
  cursor: pointer;
}

.shot__plus {
  font-size: 20px;
  line-height: 1;
}

.shot__tip {
  font-size: var(--cm-font-size-xs);
}

.tips {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}
</style>

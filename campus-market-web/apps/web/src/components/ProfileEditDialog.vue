<script setup lang="ts">
/**
 * 资料编辑弹层（昵称 + 头像，notes/20 D3）。
 *
 * <p>从顶栏抽出来做公共组件：头像悬停下拉里的「编辑资料」、个人主页（自己）的
 * 「编辑资料」按钮都复用它，避免两套编辑 UI 各自维护。
 *
 * <p>以当前会话为初值，仅在打开时回填；保存走 store.updateProfile（局部更新，
 * 没换头像就不传 avatarUrl，空串会被后端判非法）。关闭不落任何改动。
 */

import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadImage } from '@campus/api'
import { useUserStore } from '@/stores/user'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()

const session = useUserStore()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const nick = ref('')
const avatar = ref('')
const uploading = ref(false)
const saving = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

/** 打开时以会话初值回填：取消不改任何东西 */
watch(visible, (v) => {
  if (v) {
    nick.value = session.nickname
    avatar.value = session.avatarUrl
  }
})

function pickAvatar(): void {
  fileInput.value?.click()
}

function onFile(e: Event): void {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  void uploadImage(file)
    .then((url) => {
      avatar.value = url
    })
    .catch(() => undefined)
    .finally(() => {
      uploading.value = false
      input.value = ''
    })
}

async function save(): Promise<void> {
  const name = nick.value.trim()
  if (!name) {
    ElMessage.warning({ message: '昵称不能为空', duration: 2000 })
    return
  }
  if (uploading.value) {
    ElMessage.warning({ message: '头像上传中，稍候', duration: 2000 })
    return
  }
  saving.value = true
  try {
    await session.updateProfile(
      avatar.value ? { nickname: name, avatarUrl: avatar.value } : { nickname: name }
    )
    visible.value = false
    ElMessage.success({ message: '已保存', duration: 2000 })
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="编辑资料" width="400px" append-to-body>
    <div class="pf">
      <button type="button" class="pf__avatar" @click="pickAvatar">
        <img v-if="avatar" :src="avatar" class="pf__img" alt="" />
        <span v-else class="pf__t">{{ (session.nickname || '我').slice(0, 1) }}</span>
        <span class="pf__mask">更换头像</span>
        <span v-if="uploading" class="pf__loading">上传中</span>
      </button>
      <input ref="fileInput" class="pf__file" type="file" accept="image/jpeg,image/png,image/webp" @change="onFile" />
      <label class="pf__label">昵称</label>
      <el-input v-model="nick" maxlength="64" show-word-limit placeholder="请输入昵称" />
      <p class="pf__hint">昵称展示在商品列表、详情与留言中；头像支持 jpg / png / webp，不超过 5MB。</p>
    </div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.pf {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--cm-space-3);
}

.pf__avatar {
  position: relative;
  width: 72px;
  height: 72px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: var(--cm-color-brand-bg);
  cursor: pointer;
  overflow: hidden;

  &:hover .pf__mask {
    opacity: 1;
  }
}

.pf__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.pf__t {
  font-size: 28px;
  font-weight: 700;
  color: var(--cm-color-text-1);
}

.pf__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--cm-font-size-xs);
  color: #fff;
  background: rgba(31, 31, 38, 0.45);
  opacity: 0;
  transition: opacity 120ms ease-out;
}

.pf__loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--cm-font-size-xs);
  color: #fff;
  background: rgba(31, 31, 38, 0.55);
}

.pf__file {
  display: none;
}

.pf__label {
  align-self: flex-start;
  font-size: var(--cm-font-size-sm);
  font-weight: 600;
  color: var(--cm-color-text-2);
}

.pf__hint {
  margin: 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}
</style>

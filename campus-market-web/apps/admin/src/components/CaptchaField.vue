<script setup lang="ts">
/**
 * 图形验证码输入（A1 / A0 / A10 三页共用，design/03 第五章「认证三页共用规范」）
 *
 * <p>为什么做成受控组件而不是让页面各写一遍：三页的验证码语义完全一致
 * （一次性、点击换一张、key 与图片一起下发），复制三份必然漂移。
 *
 * <p>key 与用户输入**分开**回传：验证码文本只存在于 Redis，浏览器从头到尾
 * 拿不到正确答案——把答案塞进图片 URL 或隐藏域，等于把题目答案一起发给刷题的人。
 *
 * <p>后端 `campus.captcha.enabled=false` 时下发结果带 `disabled=true`，
 * 此时组件通过 `disabled` 事件通知父级把自己隐藏（本地联调不想每次都输验证码）。
 */

import { onMounted, ref } from 'vue'
import { authApi } from '@campus/api'

const props = withDefaults(defineProps<{ width?: number }>(), { width: 120 })

const code = defineModel<string>('code', { default: '' })
// ⚠ 不能叫 key：key 是 Vue 保留属性名，defineModel('key') 声明的 prop 会被
// normalizePropsOptions 直接丢弃（控制台告警 Invalid prop name），useModel 退化为
// 普通 ref，父页面的 v-model 永远收不到值——表现为提交时 captchaKey 是空串。
const captchaKey = defineModel<string>('captchaKey', { default: '' })

const emit = defineEmits<{ disabled: []; submit: [] }>()

const image = ref('')
const loading = ref(false)

/** 换一张：只在挂载与「校验失败后」调用，正常提交不刷新（避免无谓请求） */
const refresh = async (): Promise<void> => {
  loading.value = true
  try {
    const vo = await authApi.captcha()
    if (vo.disabled) {
      emit('disabled')
      return
    }
    captchaKey.value = vo.captchaKey
    image.value = vo.captchaImage
    code.value = ''
  } catch {
    // 失败提示由请求层统一 toast；图片保持为空，用户可点击「换一张」重试
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

defineExpose({ refresh })
</script>

<template>
  <div class="captcha">
    <a-input
      v-model:value="code"
      class="captcha__input"
      placeholder="图形验证码"
      :maxlength="4"
      allow-clear
      @press-enter="$emit('submit')"
    />
    <div class="captcha__img" :style="{ width: `${props.width}px` }" @click="refresh">
      <img
        v-if="image"
        :src="`data:image/png;base64,${image}`"
        alt="图形验证码"
        title="点击换一张"
      />
      <span v-else-if="loading">加载中</span>
      <span v-else class="captcha__retry">点击加载</span>
    </div>
  </div>
</template>

<style scoped>
.captcha {
  display: flex;
  align-items: center;
  gap: var(--cm-space-2);
}

.captcha__input {
  flex: 1;
}

.captcha__img {
  height: 40px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--cm-color-border);
  border-radius: var(--cm-radius-sm);
  overflow: hidden;
  cursor: pointer;
  background: var(--cm-color-bg);
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.captcha__img img {
  width: 100%;
  height: 100%;
  display: block;
}

.captcha__retry {
  user-select: none;
}
</style>

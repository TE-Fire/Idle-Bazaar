<script setup lang="ts">
/**
 * 系统通知（notes/22 批次 3）：平台发出的事件流，只有「看」这一种动作。
 *
 * <p>与私信分开：私信是会话式的（进 ChatView 按人聊），通知是单向广播，
 * 混在一起会让「未读」这个数字两头都算不清。
 *
 * <p>进页面即全部标记已读——通知没有「逐条已读」的必要，逐条点只会让用户多点几下。
 */

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatRelativeTime } from '@campus/shared'
import { userNoticeApi, type NoticeVO } from '@campus/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const session = useUserStore()

const list = ref<NoticeVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = 20
const loading = ref(false)

const finished = computed(() => list.value.length >= total.value)

async function load(reset = false): Promise<void> {
  if (loading.value) return
  if (!session.isLogin) {
    void router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  loading.value = true
  try {
    if (reset) pageNum.value = 1
    const p = await userNoticeApi.list(pageNum.value, pageSize)
    list.value = reset ? p.list : [...list.value, ...p.list]
    total.value = Number(p.total)
  } catch {
    // http 层已提示
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load(true)
  // 拉取成功后再清未读：反过来的话，请求失败＝未读被无声清空
  if (list.value.some((n) => n.readFlag === 0)) {
    void userNoticeApi.readAll().then(() => {
      list.value = list.value.map((n) => ({ ...n, readFlag: 1 }))
    })
  }
})

function more(): void {
  pageNum.value += 1
  void load()
}
</script>

<template>
  <div class="cm-shell notice">
    <header class="notice__hd">
      <h1>系统通知</h1>
      <span class="notice__n">{{ total }} 条</span>
    </header>

    <p v-if="loading && !list.length" class="notice__state">加载中…</p>
    <p v-else-if="!list.length" class="notice__state">还没有通知</p>

    <ul v-else class="notice__list">
      <li v-for="n in list" :key="n.id" class="row" :class="{ 'row--new': n.readFlag === 0 }">
        <span class="row__dot" />
        <div class="row__body">
          <span class="row__tag" :class="{ 'row__tag--i': n.type === 5 }">
            {{ n.type === 5 ? '互动' : '系统' }}
          </span>
          <p class="row__text">{{ n.content }}</p>
          <span class="row__time">{{ formatRelativeTime(n.createTime) }}</span>
        </div>
      </li>
    </ul>

    <div v-if="list.length && !finished" class="notice__more">
      <el-button :loading="loading" @click="more">加载更多</el-button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.notice {
  max-width: 760px;
  padding-top: var(--cm-space-5);
  padding-bottom: var(--cm-space-8);
}

.notice__hd {
  display: flex;
  align-items: baseline;
  gap: var(--cm-space-2);
  margin-bottom: var(--cm-space-4);
}

.notice__hd h1 {
  margin: 0;
  font-size: var(--cm-font-size-lg);
  font-weight: 600;
}

.notice__n {
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.notice__state {
  margin: var(--cm-space-8) 0;
  text-align: center;
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
}

.notice__list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-2);
}

.row {
  display: flex;
  gap: var(--cm-space-3);
  padding: var(--cm-space-4) var(--cm-space-5);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-lg);
}

.row__dot {
  flex: none;
  width: 6px;
  height: 6px;
  margin-top: 7px;
  border-radius: 50%;
  background: transparent;
}

.row--new .row__dot {
  background: #e0245e;
}

.row__body {
  flex: 1;
  min-width: 0;
}

/* 类型标：互动用品牌色，系统处置用中性灰 */
.row__tag {
  display: inline-block;
  padding: 0 6px;
  margin-bottom: 6px;
  font-size: var(--cm-font-size-xs);
  line-height: 18px;
  color: var(--cm-color-text-3);
  background: var(--cm-color-bg);
  border-radius: var(--cm-radius-sm);
}

.row__tag--i {
  color: var(--cm-color-brand-dark);
  background: var(--cm-color-brand-bg);
}

.row__text {
  margin: 0;
  font-size: var(--cm-font-size-sm);
  line-height: 1.6;
  color: var(--cm-color-text-1);
  word-break: break-word;
}

.row__time {
  display: block;
  margin-top: 4px;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-3);
}

.notice__more {
  margin-top: var(--cm-space-4);
  text-align: center;
}
</style>

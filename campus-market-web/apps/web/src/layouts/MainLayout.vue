<script setup lang="ts">
/**
 * 页面骨架（2026-09-30 按闲鱼 Web 版重构）
 *
 * <p>参考闲鱼客户端截图提炼的骨架：**通栏品牌黄导航**（logo + 胶囊搜索 + 热搜词），
 * 下面直接是灰底内容区，不再有第二条白色分类条——分类收进首页左侧的白色分类卡
 * （见 HomeView），这与闲鱼「首页左上角分类入口」的排布一致。
 *
 * <p>右侧 72px 悬浮工具栏（发布 / 消息 / 对比 / 回顶部）也是闲鱼的标志性元素，
 * 桌面端常驻。对比入口带角标（已选几件），让 CompareBar 之外多一个稳定入口。
 *
 * <p>配色纪律不变：黄底永远配黑字（design/01 §4.2），主色只落在搜索按钮 /
 * 发布按钮 / 对比角标这几处（design/01 原则 1）。
 */

import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { CAMPUSES } from '@campus/shared'
import { ChatDotRound, CopyDocument, Position, Top } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { uploadImage } from '@campus/api'
import { useCompareStore } from '@/stores/compare'
import { useUserStore } from '@/stores/user'
import CompareBar from '@/components/CompareBar.vue'

const router = useRouter()
const compare = useCompareStore()
const session = useUserStore()

const keyword = ref('')

/** 顶栏校区切换：骨架期只做展示，不参与筛选（筛选由侧栏校区组负责） */
const campus = ref<string>(CAMPUSES[0])

/** 热搜词：闲鱼把一批高频词铺在搜索框下面。这里写死一组校园高频词，点击即搜 */
const HOT_WORDS = ['笔记本电脑', '考研资料', '自行车', 'iPad', '电动车', '台灯', '吉他', '篮球'] as const

/* ── 资料编辑（notes/20 D3：昵称 + 头像，顶栏弹层）────────────── */

const editOpen = ref(false)
const editNick = ref('')
const editAvatar = ref('')
const uploading = ref(false)
const saving = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

/** 打开编辑弹层：以当前会话为初值，取消不改任何东西 */
function openEdit(): void {
  editNick.value = session.nickname
  editAvatar.value = session.avatarUrl
  editOpen.value = true
}

function pickAvatar(): void {
  fileInput.value?.click()
}

/** 选完图即上传（两步票据制）：成功换预览；失败由 http 层统一提示，这里只收尾状态 */
function onFile(e: Event): void {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  void uploadImage(file)
    .then((url) => {
      editAvatar.value = url
    })
    .catch(() => undefined)
    .finally(() => {
      uploading.value = false
      input.value = ''
    })
}

async function saveProfile(): Promise<void> {
  const nick = editNick.value.trim()
  if (!nick) {
    ElMessage.warning({ message: '昵称不能为空', duration: 2000 })
    return
  }
  if (uploading.value) {
    ElMessage.warning({ message: '头像上传中，稍候', duration: 2000 })
    return
  }
  saving.value = true
  try {
    // 局部更新：没换过头像就不传 avatarUrl——传空串会被服务端判非法（4021）
    await session.updateProfile(
      editAvatar.value ? { nickname: nick, avatarUrl: editAvatar.value } : { nickname: nick }
    )
    editOpen.value = false
    ElMessage.success({ message: '已保存', duration: 2000 })
  } finally {
    saving.value = false
  }
}

function onSearch(): void {
  void router.push({ name: 'list', query: { q: keyword.value || undefined } })
}

function onHot(word: string): void {
  keyword.value = word
  void router.push({ name: 'list', query: { q: word } })
}

function backTop(): void {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

/** 退出登录：清本地会话回首页（后端注销失败也清，见 stores/user） */
async function logout(): Promise<void> {
  await session.logout()
  void router.push({ name: 'home' })
}
</script>

<template>
  <div class="layout">
    <header class="nav">
      <div class="nav__in">
        <RouterLink :to="{ name: 'home' }" class="logo">校园二手</RouterLink>

        <!-- 搜索块：白胶囊输入框 + 下方热搜词（闲鱼的排法） -->
        <div class="search-block">
          <div class="search">
            <input
              v-model="keyword"
              class="search__input"
              placeholder="想要什么，搜一搜"
              @keyup.enter="onSearch"
            />
            <button type="button" class="search__btn" @click="onSearch">
              <el-icon :size="14"><Position /></el-icon>搜索
            </button>
          </div>
          <div class="hot">
            <a v-for="w in HOT_WORDS" :key="w" href="javascript:void(0)" @click="onHot(w)">{{ w }}</a>
          </div>
        </div>

        <nav class="nav__right">
          <el-select v-model="campus" class="campus" size="default">
            <el-option v-for="c in CAMPUSES" :key="c" :label="c" :value="c" />
          </el-select>
          <RouterLink :to="{ name: 'publish' }" class="nav__publish">
            <el-icon :size="14"><Position /></el-icon>发布闲置
          </RouterLink>
          <RouterLink :to="{ name: 'chat' }" class="lnk"><el-icon :size="16"><ChatDotRound /></el-icon>消息</RouterLink>
          <a class="lnk"><el-icon :size="16"><CopyDocument /></el-icon>订单</a>
          <!-- 登录态：未登录显示入口，已登录显示头像 + 昵称 + 我的商品 + 退出 -->
          <RouterLink v-if="!session.isLogin" :to="{ name: 'login' }" class="lnk">登录</RouterLink>
          <template v-else>
            <button type="button" class="nav__avatar" title="编辑资料" @click="openEdit">
              <img v-if="session.avatarUrl" :src="session.avatarUrl" class="nav__avatar-img" alt="" />
              <span v-else class="nav__avatar-t">{{ (session.nickname || '我').slice(0, 1) }}</span>
            </button>
            <RouterLink :to="{ name: 'my-items' }" class="lnk">{{ session.nickname || '我的' }}</RouterLink>
            <a class="lnk" @click="openEdit">编辑</a>
            <a class="lnk" @click="logout">退出</a>
          </template>
        </nav>
      </div>
    </header>

    <main class="main">
      <RouterView />
    </main>

    <!-- 右侧悬浮工具栏：闲鱼的标志性竖排小窗 -->
    <aside class="rail">
      <RouterLink :to="{ name: 'publish' }" class="rail__item rail__item--hot">
        <span class="rail__ico"><el-icon :size="18"><Position /></el-icon></span>
        <span>发布</span>
      </RouterLink>
      <RouterLink :to="{ name: 'chat' }" class="rail__item">
        <span class="rail__ico"><el-icon :size="18"><ChatDotRound /></el-icon></span>
        <span>消息</span>
      </RouterLink>
      <button type="button" class="rail__item" @click="router.push({ name: 'compare' })">
        <span class="rail__ico">
          <el-icon :size="18"><CopyDocument /></el-icon>
          <i v-if="compare.count" class="rail__badge">{{ compare.count }}</i>
        </span>
        <span>对比</span>
      </button>
      <button type="button" class="rail__item" @click="backTop">
        <span class="rail__ico"><el-icon :size="18"><Top /></el-icon></span>
        <span>顶部</span>
      </button>
    </aside>

    <CompareBar />

    <!-- 资料编辑弹层：Web 端没有独立「我的」页，编辑入口收在顶栏（notes/20 §五） -->
    <el-dialog v-model="editOpen" title="编辑资料" width="400px" append-to-body>
      <div class="pf">
        <button type="button" class="pf__avatar" @click="pickAvatar">
          <img v-if="editAvatar" :src="editAvatar" class="pf__img" alt="" />
          <span v-else class="pf__t">{{ (session.nickname || '我').slice(0, 1) }}</span>
          <span class="pf__mask">更换头像</span>
          <span v-if="uploading" class="pf__loading">上传中</span>
        </button>
        <input ref="fileInput" class="pf__file" type="file" accept="image/jpeg,image/png,image/webp" @change="onFile" />
        <label class="pf__label">昵称</label>
        <el-input v-model="editNick" maxlength="64" show-word-limit placeholder="请输入昵称" />
        <p class="pf__hint">昵称展示在商品列表、详情与留言中；头像支持 jpg / png / webp，不超过 5MB。</p>
      </div>
      <template #footer>
        <el-button @click="editOpen = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.layout {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

/* ── 通栏品牌黄导航 ─────────────────────────────────────── */

.nav {
  position: sticky;
  top: 0;
  z-index: 30;
  background: var(--cm-color-brand);
  /* 黄底下不描边，靠内容区的灰底分层（闲鱼做法） */
  box-shadow: 0 1px 0 rgba(31, 31, 38, 0.06);
}

.nav__in {
  max-width: var(--cm-layout-content-max);
  margin: 0 auto;
  padding: var(--cm-space-3) var(--cm-space-8) var(--cm-space-2);
  display: flex;
  align-items: flex-start;
  gap: var(--cm-space-6);
}

.logo {
  padding-top: var(--cm-space-2);
  font-size: 22px;
  font-weight: 900;
  letter-spacing: 1px;
  color: var(--cm-color-text-1);
  white-space: nowrap;
}

/* ── 搜索块 ─────────────────────────────────────────────── */

.search-block {
  flex: 1;
  max-width: 560px;
}

/* 白胶囊：闲鱼的搜索框是一整枚药丸，按钮嵌在右端 */
.search {
  height: 40px;
  display: flex;
  align-items: center;
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-pill);
  padding: 0 var(--cm-space-3) 0 var(--cm-space-4);
  box-shadow: var(--cm-shadow-1);
}

.search__input {
  flex: 1;
  min-width: 0;
  font-size: var(--cm-font-size-base);
  color: var(--cm-color-text-1);
  background: none;
  border: none;
  outline: none;

  &::placeholder {
    color: var(--cm-color-text-3);
  }
}

.search__btn {
  height: 30px;
  padding: 0 var(--cm-space-4);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--cm-font-size-sm);
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
  border: none;
  border-radius: var(--cm-radius-pill);
  cursor: pointer;

  &:hover {
    background: var(--cm-color-brand-dark);
  }
}

/* 热搜词：黄底上的深色小字，透明度而不是换灰——灰在黄底上会显脏 */
.hot {
  display: flex;
  align-items: center;
  gap: var(--cm-space-3);
  padding: var(--cm-space-1) var(--cm-space-2) 0;
  overflow: hidden;

  a {
    font-size: var(--cm-font-size-xs);
    color: rgba(31, 31, 38, 0.72);
    white-space: nowrap;

    &:hover {
      color: var(--cm-color-text-1);
      font-weight: 600;
    }
  }
}

/* ── 右侧动作区 ─────────────────────────────────────────── */

.nav__right {
  margin-left: auto;
  padding-top: var(--cm-space-1);
  display: flex;
  align-items: center;
  gap: var(--cm-space-5);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-1);
  white-space: nowrap;
}

.campus {
  width: 108px;
  flex: none;
}

.campus :deep(.el-select__wrapper) {
  background: rgba(255, 255, 255, 0.85);
  border-radius: var(--cm-radius-pill);
  box-shadow: none;
}

/* 发布按钮是黑底黄字——黄底页面上唯一的反色块，权重最高（design/01 原则 1） */
.nav__publish {
  height: 32px;
  padding: 0 var(--cm-space-4);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 700;
  color: var(--cm-color-brand);
  background: var(--cm-color-text-1);
  border-radius: var(--cm-radius-pill);

  &:hover {
    opacity: 0.88;
  }
}

.lnk {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: 600;

  &:hover {
    opacity: 0.75;
  }
}

/* 顶栏头像：黄底上的白环小圆，点击进资料编辑（notes/20 D4） */
.nav__avatar {
  width: 28px;
  height: 28px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(255, 255, 255, 0.9);
  border-radius: 50%;
  background: var(--cm-color-brand-dark);
  cursor: pointer;
  overflow: hidden;

  &:hover {
    opacity: 0.85;
  }
}

.nav__avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.nav__avatar-t {
  font-size: 13px;
  font-weight: 700;
  color: var(--cm-color-brand);
}

/* ── 内容区 ─────────────────────────────────────────────── */

.main {
  flex: 1;
}

/* ── 右侧悬浮工具栏 ─────────────────────────────────────── */

.rail {
  position: fixed;
  right: var(--cm-space-3);
  top: 30%;
  z-index: 25;
  width: var(--cm-layout-rail-w);
  display: flex;
  flex-direction: column;
  gap: var(--cm-space-1);
  padding: var(--cm-space-2);
  background: var(--cm-color-surface);
  border-radius: var(--cm-radius-2xl);
  box-shadow: var(--cm-shadow-2);
}

.rail__item {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: var(--cm-space-2) 0;
  font-size: var(--cm-font-size-xs);
  color: var(--cm-color-text-2);
  background: none;
  border: none;
  border-radius: var(--cm-radius-lg);
  cursor: pointer;
  text-decoration: none;

  &:hover {
    color: var(--cm-color-text-1);
    background: var(--cm-color-bg);
  }
}

.rail__ico {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
}

.rail__item--hot .rail__ico {
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
}

.rail__badge {
  position: absolute;
  right: -4px;
  top: -4px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 11px;
  font-style: normal;
  line-height: 16px;
  color: #fff;
  background: var(--cm-color-price);
  border-radius: var(--cm-radius-pill);
  text-align: center;
}

/* 窄屏：悬浮栏让位给底部 CompareBar，避免小屏重叠 */
@media (max-width: 1023px) {
  .rail {
    display: none;
  }
}

/* ── 资料编辑弹层 ───────────────────────────────────────── */

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
  color: var(--cm-color-brand-text);
}

/*  hover 蒙层：告诉用户这块能点（桌面端没有长按换头像的心智） */
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

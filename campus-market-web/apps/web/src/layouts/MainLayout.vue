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

import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, ChatDotRound, Compass, CopyDocument, Edit, Goods, Position, Star, SwitchButton, Top, User } from '@element-plus/icons-vue'
import { userDictApi } from '@campus/api'
import { useCompareStore } from '@/stores/compare'
import { useUserStore } from '@/stores/user'
import CompareBar from '@/components/CompareBar.vue'
import ProfileEditDialog from '@/components/ProfileEditDialog.vue'

const route = useRoute()
const router = useRouter()
const compare = useCompareStore()
const session = useUserStore()

const keyword = ref('')

/**
 * 顶栏校区切换：**单选、作用于全站列表**，选项来自字典（'' = 全部校区）。
 *
 * <p>状态放 URL `?campus=<校区名>` 而不是组件内 ref：这样刷新 / 分享链接 / 浏览器后退
 * 都能保持，首页也能直接从 query 读初值。选项以前写死三个（中心校区 / 南苑宿舍区），
 * 与库里字典（主校区 / 东校区 / 北校区）对不上，选哪个都筛不出商品。
 */
const campusOptions = ref<string[]>([])
const campus = computed({
  get: () => (route.query.campus as string) ?? '',
  set: (v: string) => {
    // 切校区 = 看该校区的东西，统一落到列表页；保留已有的关键词 / 分类条件
    void router.push({ name: 'list', query: { ...route.query, campus: v || undefined } })
  }
})

onMounted(async () => {
  try {
    const dict = await userDictApi.all()
    campusOptions.value = [...dict.campuses]
      .sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
      .map((c) => c.name)
  } catch {
    // 字典不可用就只剩「全部校区」，不影响浏览
  }
})

/** 热搜词：闲鱼把一批高频词铺在搜索框下面。这里写死一组校园高频词，点击即搜 */
const HOT_WORDS = ['笔记本电脑', '考研资料', '自行车', 'iPad', '电动车', '台灯', '吉他', '篮球'] as const

/* ── 资料编辑：弹层抽到 ProfileEditDialog，这里只持开关 ────── */

const editOpen = ref(false)
function openEdit(): void {
  editOpen.value = true
}

/** 头像首字（无头像时兜底） */
const initial = computed(() => (session.nickname || '我').slice(0, 1))

/** 「我的主页」= 自己的个人主页，复用 user-profile 路由（self 自适应） */
function goMe(): void {
  void router.push({ name: 'user-profile', params: { id: session.userId } })
}
function goMyItems(): void {
  void router.push({ name: 'my-items' })
}
function goFollow(): void {
  void router.push({ name: 'follow' })
}

/** 系统通知：平台广播（举报处置结果等），与私信两个入口分开 */
function goNotices(): void {
  void router.push({ name: 'notices' })
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
            <el-option label="全部校区" value="" />
            <el-option v-for="c in campusOptions" :key="c" :label="c" :value="c" />
          </el-select>
          <RouterLink :to="{ name: 'publish' }" class="nav__publish">
            <el-icon :size="14"><Position /></el-icon>发布闲置
          </RouterLink>
          <RouterLink :to="{ name: 'moments' }" class="lnk"><el-icon :size="16"><Compass /></el-icon>广场</RouterLink>
          <RouterLink :to="{ name: 'chat' }" class="lnk"><el-icon :size="16"><ChatDotRound /></el-icon>消息</RouterLink>
          <RouterLink :to="{ name: 'orders' }" class="lnk"><el-icon :size="16"><CopyDocument /></el-icon>订单</RouterLink>
          <!-- 登录态：头像悬停弹出菜单（关注 / 我的商品 / 我的主页 / 编辑 / 退出）
               都收在这里，顶栏不再散落；未登录只留入口 -->
          <template v-if="session.isLogin">
            <el-dropdown class="nav__user" trigger="hover" placement="bottom-end">
              <span class="nav__user-trigger">
                <button type="button" class="nav__avatar" title="点击查看菜单">
                  <img v-if="session.avatarUrl" :src="session.avatarUrl" class="nav__avatar-img" alt="" />
                  <span v-else class="nav__avatar-t">{{ initial }}</span>
                </button>
              </span>
              <template #dropdown>
                <el-dropdown-menu class="udrop">
                  <div class="udrop__head">
                    <div class="udrop__ava">
                      <img v-if="session.avatarUrl" :src="session.avatarUrl" alt="" />
                      <span v-else>{{ initial }}</span>
                    </div>
                    <div class="udrop__meta">
                      <p class="udrop__name">{{ session.nickname || '我的' }}</p>
                      <p class="udrop__sub">
                        <span v-if="session.authStatus === 2" class="udrop__ok">已认证</span>
                        <span>信用 {{ session.creditScore }} 分</span>
                      </p>
                    </div>
                  </div>
                  <el-dropdown-item @click="goMe">
                    <el-icon><User /></el-icon>我的主页
                  </el-dropdown-item>
                  <el-dropdown-item @click="goMyItems">
                    <el-icon><Goods /></el-icon>我的商品
                  </el-dropdown-item>
                  <el-dropdown-item @click="goFollow">
                    <el-icon><Star /></el-icon>我的关注
                  </el-dropdown-item>
                  <el-dropdown-item @click="goNotices">
                    <el-icon><Bell /></el-icon>系统通知
                  </el-dropdown-item>
                  <el-dropdown-item @click="openEdit">
                    <el-icon><Edit /></el-icon>编辑资料
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="logout">
                    <el-icon><SwitchButton /></el-icon>退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <RouterLink v-else :to="{ name: 'login' }" class="lnk">登录</RouterLink>
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

    <!-- 资料编辑弹层：头像下拉「编辑资料」、个人主页「编辑资料」共用 -->
    <ProfileEditDialog v-model="editOpen" />
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

/* 右侧动作区：margin-left:auto 推到最右端；整组与搜索框垂直居中 */
.nav__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--cm-space-5);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-1);
  white-space: nowrap;
}

/* 头像尺寸跟随导航栏节奏：取与搜索框（40px）相近的 36px，hover 轻微上浮。
   注意：尺寸必须写死，不能走 scoped 里的 :root 变量（scoped 属性选择器
   作用不到 <html>，变量不会被声明，var() 回退 auto 会让头像撑成原图大小） */

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

/* 顶栏头像：黄底上的白环小圆，悬停弹菜单（notes/20 D4） */
.nav__user {
  margin-left: 0;
}

.nav__user-trigger {
  display: inline-flex;
  outline: none;
  cursor: pointer;
}

.nav__avatar {
  width: 36px;
  height: 36px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 2px solid rgba(255, 255, 255, 0.92);
  border-radius: 50%;
  background: var(--cm-color-brand-dark);
  box-shadow: 0 1px 4px rgba(31, 31, 38, 0.18);
  cursor: pointer;
  overflow: hidden;
  transition: transform 120ms ease-out, box-shadow 120ms ease-out;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 3px 10px rgba(31, 31, 38, 0.26);
  }
}

.nav__avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.nav__avatar-t {
  font-size: 15px;
  font-weight: 700;
  color: var(--cm-color-brand);
}

/* ── 头像悬停下拉菜单 ───────────────────────────────────── */

.udrop {
  padding: var(--cm-space-2) 0;
  min-width: 184px;

  /* 头部资料卡 */
  .udrop__head {
    display: flex;
    align-items: center;
    gap: var(--cm-space-3);
    padding: var(--cm-space-2) var(--cm-space-4) var(--cm-space-3);
  }

  .udrop__ava {
    width: 44px;
    height: 44px;
    flex: none;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    overflow: hidden;
    background: var(--cm-color-brand-bg);
    color: var(--cm-color-text-1);
    font-weight: 700;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .udrop__meta {
    min-width: 0;
  }

  .udrop__name {
    margin: 0;
    font-size: var(--cm-font-size-md);
    font-weight: 700;
    color: var(--cm-color-text-1);
  }

  .udrop__sub {
    margin: 2px 0 0;
    display: flex;
    align-items: center;
    gap: var(--cm-space-2);
    font-size: var(--cm-font-size-xs);
    color: var(--cm-color-text-3);
  }

  .udrop__ok {
    color: var(--cm-color-ok);
  }

  /* 下拉项：覆盖 el-dropdown-item 默认行高与留白 */
  :deep(.el-dropdown-menu__item) {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: var(--cm-font-size-sm);
    color: var(--cm-color-text-1);
    padding: 0 var(--cm-space-4);

    .el-icon {
      font-size: 16px;
    }
  }

  :deep(.el-dropdown-menu__item--divided) {
    margin-top: 4px;
    border-top: 1px solid var(--cm-color-border);
  }

  /* 「退出登录」用红色，抬高特异性盖过上面统一的 item 颜色 */
  :deep(.el-dropdown-menu__item.udrop__logout) {
    color: var(--cm-color-danger, #cf1322);
  }
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
</style>

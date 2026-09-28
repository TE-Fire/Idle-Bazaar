<script setup lang="ts">
/**
 * 页面骨架：nav（60px，sticky）+ cat（44px）+ 内容 + 对比条
 * 结构照搬 design/01 §3.2 的 ASCII 图，尺寸全部走令牌（--cm-layout-*）。
 */

import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CAMPUSES, CATEGORIES, type Category } from '@campus/shared'
import CompareBar from '@/components/CompareBar.vue'

const route = useRoute()
const router = useRouter()

/** 搜索框里的分类下拉：默认「全部分类」 */
const searchCat = ref<string>('')
const keyword = ref('')

/** 顶栏校区切换：骨架期只做展示，不参与筛选（筛选由侧栏校区组负责） */
const campus = ref<string>(CAMPUSES[0])

/** 分类条高亮：首页为「首页」，列表页跟随 ?cat */
const activeCat = computed<Category>(() => {
  const cat = route.query.cat as string | undefined
  return (cat && (CATEGORIES as readonly string[]).includes(cat) ? cat : '首页') as Category
})

function gotoCat(cat: Category): void {
  if (cat === '首页') {
    void router.push({ name: 'home' })
    return
  }
  void router.push({ name: 'list', query: { cat } })
}

function onSearch(): void {
  void router.push({
    name: 'list',
    query: { q: keyword.value || undefined, cat: searchCat.value || undefined }
  })
}
</script>

<template>
  <div class="layout">
    <header class="nav">
      <div class="nav__in">
        <RouterLink :to="{ name: 'home' }" class="logo">校园二手</RouterLink>

        <el-select v-model="campus" class="campus" size="large">
          <el-option v-for="c in CAMPUSES" :key="c" :label="c" :value="c" />
        </el-select>

        <!-- 搜索框：外层 2px 黄描边，高 36px，左分类下拉黄底，右 64px 黄按钮 -->
        <div class="search">
          <el-select v-model="searchCat" class="search__cat" placeholder="全部分类">
            <el-option label="全部分类" value="" />
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
          <input v-model="keyword" class="search__input" placeholder="搜闲置、教材、数码…" @keyup.enter="onSearch" />
          <button type="button" class="search__btn" @click="onSearch">搜索</button>
        </div>

        <nav class="nav__right">
          <RouterLink :to="{ name: 'publish' }" class="nav__publish">发布闲置</RouterLink>
          <a class="lnk">消息</a>
          <a class="lnk">我的</a>
        </nav>
      </div>
    </header>

    <nav v-if="!route.meta.hideCat" class="cat">
      <div class="cat__in">
        <a
          v-for="c in CATEGORIES"
          :key="c"
          :class="{ on: activeCat === c }"
          href="javascript:void(0)"
          @click="gotoCat(c)"
        >
          {{ c }}
        </a>
        <div class="cat__right">
          <span>我的收藏</span><span>浏览历史</span><span>校园面交点</span>
        </div>
      </div>
    </nav>

    <main class="main">
      <RouterView />
    </main>

    <CompareBar />
  </div>
</template>

<style scoped lang="scss">
.layout {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

/* ── nav 60px ───────────────────────────────────────────── */

.nav {
  position: sticky;
  top: 0;
  z-index: 30;
  height: var(--cm-layout-nav-h);
  background: var(--cm-color-surface);
  border-bottom: 1px solid var(--cm-color-border);
}

.nav__in {
  max-width: var(--cm-layout-content-max);
  height: 100%;
  margin: 0 auto;
  padding: 0 var(--cm-space-6);
  display: flex;
  align-items: center;
  gap: var(--cm-space-5);
}

.logo {
  font-size: var(--cm-font-size-md);
  font-weight: 800;
  color: var(--cm-color-text-1);
  white-space: nowrap;
}

.campus {
  width: 120px;
  flex: none;
}

.search {
  flex: 1;
  max-width: 520px;
  height: 36px;
  display: flex;
  align-items: stretch;
  border: 2px solid var(--cm-color-brand);
  border-radius: var(--cm-radius-md);
  overflow: hidden;
}

/* 左侧分类下拉：黄底 100（design/01 §4.3） */
.search__cat {
  width: 110px;
  flex: none;
  background: var(--cm-color-brand-bg);
  border-right: 1px solid var(--cm-color-brand-border);
}

.search__input {
  flex: 1;
  min-width: 0;
  padding: 0 var(--cm-space-3);
  font-size: var(--cm-font-size-base);
  color: var(--cm-color-text-1);
  background: var(--cm-color-surface);
  border: none;
  outline: none;
}

.search__btn {
  width: 64px;
  flex: none;
  font-size: var(--cm-font-size-base);
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
  border: none;
  cursor: pointer;
}

.nav__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--cm-space-5);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-2);
  white-space: nowrap;
}

/* 发布按钮是主色唯一的落点之一（design/01 原则 1） */
.nav__publish {
  height: 30px;
  padding: 0 var(--cm-space-4);
  line-height: 30px;
  font-weight: 700;
  color: var(--cm-color-text-1);
  background: var(--cm-color-brand);
  border-radius: var(--cm-radius-md);
}

.lnk:hover {
  color: var(--cm-color-text-1);
}

/* ── cat 44px ───────────────────────────────────────────── */

.cat {
  background: var(--cm-color-surface);
  border-bottom: 1px solid var(--cm-color-border);
}

.cat__in {
  max-width: var(--cm-layout-content-max);
  height: var(--cm-layout-cat-h);
  margin: 0 auto;
  padding: 0 var(--cm-space-6);
  display: flex;
  align-items: center;
  gap: var(--cm-space-6);
  overflow-x: auto;
}

.cat__in a {
  font-size: var(--cm-font-size-md);
  color: var(--cm-color-text-2);
  white-space: nowrap;
  padding-bottom: 2px;
}

.cat__in a.on {
  color: var(--cm-color-text-1);
  font-weight: 700;
  border-bottom: 3px solid var(--cm-color-brand);
}

.cat__right {
  margin-left: auto;
  display: flex;
  gap: var(--cm-space-4);
  font-size: var(--cm-font-size-sm);
  color: var(--cm-color-text-3);
  white-space: nowrap;
}

.main {
  flex: 1;
}
</style>

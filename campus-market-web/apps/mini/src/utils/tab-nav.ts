/**
 * tab 页跳转
 *
 * <p>2026-10-28 起底部导航改为自绘（components/TabBar.vue），`pages.json` 里的原生
 * tabBar 已删除，因此 `uni.switchTab` 会直接失败——所有 tab 之间的跳转必须走这里。
 * reLaunch 的语义与原 switchTab 一致（清空页面栈、页面重建），入口统一后
 * 各页面不再各写一套。
 */

export type TabKey = 'home' | 'category' | 'publish' | 'chat' | 'me'

export const TAB_URL: Record<TabKey, string> = {
  home: '/pages/index/index',
  category: '/pages/category/index',
  publish: '/pages/publish/index',
  chat: '/pages/chat/list',
  me: '/pages/me/index'
}

/**
 * 已经在目标页就不重进：reLaunch 同页会白屏一帧，且丢掉已填的表单
 *
 * <p>url 必须带前导 `/`——H5 下传 `pages/xxx/index` 不报错也不跳转（Promise 都是 undefined），
 * 只有绝对路径才生效，小程序端同样只认绝对路径。
 */
export function goTab(key: TabKey): void {
  const url = TAB_URL[key]
  const pages = getCurrentPages()
  const cur = pages[pages.length - 1]
  if (cur && `/${cur.route}` === url) return
  void uni.reLaunch({ url })
}

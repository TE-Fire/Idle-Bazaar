/**
 * 系统通知未读数（design/01 §4.8）
 *
 * <p>红点出现在两处：顶栏头像菜单、通知页头部。各存一份 ref 的后果是「页内读完、顶栏还亮」——
 * 顶栏只在挂载时取一次，布局不随路由重建。所以未读数必须收进 store 单点持有。
 *
 * <p>只存**计数**，不存列表：列表的刷新时机（翻页、标已读后的本地态）与未读数无关，
 * 混在一起会让"取列表"顺手改红点，反而难对账。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { userNoticeApi } from '@campus/api'

export const useNoticeStore = defineStore('notice', () => {
  const unread = ref(0)
  const hasUnread = computed(() => unread.value > 0)

  /** 拉服务端未读数；失败按 0 处理——红点误亮比不亮更烦人，也不能因此挡住页面 */
  async function refresh(): Promise<void> {
    try {
      unread.value = Number(await userNoticeApi.unread()) || 0
    } catch {
      unread.value = 0
    }
  }

  /** 未登录 / 退出登录：红点必须灭 */
  function reset(): void {
    unread.value = 0
  }

  /** 点开一封：本地先减（红点立刻落下），服务端调用由调用方发，不必等往返 */
  function consumeOne(): void {
    if (unread.value > 0) unread.value -= 1
  }

  /** 一键已读 */
  function consumeAll(): void {
    unread.value = 0
  }

  return { unread, hasUnread, refresh, reset, consumeOne, consumeAll }
})

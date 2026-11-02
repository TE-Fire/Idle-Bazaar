import { REPORT_REASONS, REPORT_TARGET, reportApi } from '@/api/social'
import { getSession } from '@/utils/session'
import { cmActions } from '@/utils/dialog'

/**
 * 举报入口（notes/22 批次 3）。
 *
 * <p>原因列表走自绘动作菜单（{@link cmActions}）而不是系统 actionSheet：
 * 系统菜单固定白底黑字，与本项目暖橙主题不搭；调用方页面挂一个 CmDialogHost 即可。
 */
export async function reportTarget(targetType: number, targetId: string): Promise<void> {
  if (!getSession().tokenValue) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  if (!targetId) return

  const index = await cmActions('选择举报原因', [...REPORT_REASONS])
  if (index < 0) return
  submitReport(targetType, targetId, REPORT_REASONS[index])
}

/**
 * 提交举报：原因已选定，只负责发请求。
 *
 * <p>给自绘原因面板（商品详情「更多 → 举报」）复用——那里先弹自己的面板，
 * 再调系统 actionSheet 会出现「一个面板未收起又开下一个」，小程序端第二次直接不弹。
 */
export function submitReport(targetType: number, targetId: string, reason: string): void {
  if (!targetId || !reason) return
  reportApi
    .submit(targetType, targetId, reason)
    .then(() => uni.showToast({ title: '举报已提交', icon: 'success' }))
    .catch(() => undefined)
}

export { REPORT_TARGET }

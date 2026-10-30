import { REPORT_REASONS, REPORT_TARGET, reportApi } from '@/api/social'
import { getSession } from '@/utils/session'

/**
 * 举报入口（notes/22 批次 3）。
 *
 * <p>用系统 actionSheet 而不是自绘弹层：举报是低频动作，原生菜单的点击区与收起手势都更稳，
 * 自绘一套还要处理遮罩、键盘、刘海屏三件事。
 */
export function reportTarget(targetType: number, targetId: string): void {
  if (!getSession().tokenValue) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    void uni.navigateTo({ url: '/pages/login/index' })
    return
  }
  if (!targetId) return

  uni.showActionSheet({
    itemList: [...REPORT_REASONS],
    success: (res) => {
      submitReport(targetType, targetId, REPORT_REASONS[res.tapIndex])
    }
  })
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

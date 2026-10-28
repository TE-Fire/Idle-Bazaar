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
      const reason = REPORT_REASONS[res.tapIndex]
      reportApi
        .submit(targetType, targetId, reason)
        .then(() => uni.showToast({ title: '举报已提交', icon: 'success' }))
        .catch(() => undefined)
    }
  })
}

export { REPORT_TARGET }

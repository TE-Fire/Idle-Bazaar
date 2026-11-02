/**
 * 下单 / 付款前的确认弹窗。
 *
 * <p>用系统 modal 而不是自绘半屏面板：确认框要的是「快」和「不可误触」，
 * 自绘面板反而会被当成普通操作区。`content` 的换行在 H5 与真机都能识别，
 * 明细直接按行拼。
 *
 * <p>按钮文案控制在 4 个字以内——微信小程序对 `confirmText` 有长度限制，
 * 超出会被截断成「确认」。
 */

export interface PayConfirmOptions {
  title: string
  /** 明细行，一行一条；第一行建议写「会扣多少」 */
  lines: string[]
  confirmText?: string
}

export function confirmPay(opts: PayConfirmOptions): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: opts.title,
      content: opts.lines.join('\n'),
      confirmText: opts.confirmText ?? '确认支付',
      cancelText: '再想想',
      confirmColor: '#FF5000',
      success: (r) => resolve(!!r.confirm),
      fail: () => resolve(false)
    })
  })
}

/**
 * 截断过长的商品名。
 *
 * <p>系统 modal 的正文是居中的，长标题换行后会把整块撑成两行不对称的文字，
 * 反而看不清重点；因此这里只留够认得出的长度，完整标题在页面上本来就看得见。
 */
export function clip(text: string, max = 18): string {
  const t = text.trim()
  return t.length > max ? `${t.slice(0, max)}…` : t
}

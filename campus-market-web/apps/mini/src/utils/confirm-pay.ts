/**
 * 下单 / 付款前的确认弹窗。
 *
 * <p>走自绘弹窗（{@link cmConfirm}）而不是系统 modal：金额、商品、面交点三行要按
 * 「标签右对齐 + 重点值加粗」排，系统 modal 只有一整段居中文本，长标题一折行就
 * 看不出重点在哪。确认框仍然只有两个按钮、遮罩不可点穿，防误触的诉求不变。
 */

import { cmConfirm, type DialogLine } from './dialog'

export interface PayConfirmOptions {
  title: string
  /** 明细行：第一行建议写商品、第二行写「会扣多少」并标 strong */
  lines: DialogLine[]
  /** 底部口径说明：付款后进入什么状态、什么时候结算 */
  note?: string
  confirmText?: string
}

export function confirmPay(opts: PayConfirmOptions): Promise<boolean> {
  return cmConfirm({
    title: opts.title,
    lines: opts.lines,
    note: opts.note,
    confirmText: opts.confirmText ?? '确认支付',
    cancelText: '再想想'
  })
}

/**
 * 截断过长的商品名。
 *
 * <p>明细行是「标签 + 值」的一行布局，标题超过一行会把整张卡撑高、重点值被挤下去；
 * 完整标题在页面上本来就看得见，这里只留够认得出的长度。
 */
export function clip(text: string, max = 18): string {
  const t = text.trim()
  return t.length > max ? `${t.slice(0, max)}…` : t
}

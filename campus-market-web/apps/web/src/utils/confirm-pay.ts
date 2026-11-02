/**
 * 下单 / 付款前的二次确认。
 *
 * <p>三处入口（商品详情下单、议价卡去付款、订单页付款）文案不同但结构一致：
 * 一句结论 + 若干条明细 + 一条提醒。做成 VNode 而不是 HTML 串——商品标题是
 * 用户输入，拼 HTML 就得开 `dangerouslyUseHTMLString`，等于把 XSS 交给卖家。
 *
 * <p>确认框由 body 挂载，scoped 样式进不去，所以这里只用令牌变量 + 兜底色。
 */

import { h, type VNode } from 'vue'
import { ElMessageBox } from 'element-plus'

export interface PayConfirmLine {
  label: string
  value: string
  /** 金额这条要跳出来，其余按次要信息排 */
  strong?: boolean
}

export interface PayConfirmOptions {
  title: string
  /** 结论句：说明点下去会发生什么 */
  headline: string
  lines?: PayConfirmLine[]
  note?: string
  confirmText: string
}

export async function confirmPay(opts: PayConfirmOptions): Promise<boolean> {
  const blocks: VNode[] = [
    h('p', { style: 'margin:0 0 12px;font-size:14px;color:var(--cm-color-text-1,#1F1F26)' }, opts.headline)
  ]
  for (const l of opts.lines ?? []) {
    blocks.push(
      h('p', { style: 'margin:0 0 6px;font-size:13px;color:var(--cm-color-text-2,#5E5E68)' }, [
        `${l.label}：`,
        h(
          'span',
          {
            style: l.strong
              ? 'color:var(--cm-color-price,#FF5000);font-weight:600;font-size:15px'
              : 'color:var(--cm-color-text-1,#1F1F26)'
          },
          l.value
        )
      ])
    )
  }
  if (opts.note) {
    blocks.push(
      h('p', { style: 'margin:12px 0 0;font-size:12px;color:var(--cm-color-text-3,#9A9AA4)' }, opts.note)
    )
  }

  try {
    await ElMessageBox.confirm(h('div', { style: 'line-height:1.6' }, blocks), opts.title, {
      confirmButtonText: opts.confirmText,
      cancelButtonText: '再想想',
      type: 'warning'
    })
    return true
  } catch {
    return false
  }
}

/**
 * 主题化弹窗（确认 / 提示 / 输入 / 动作菜单）
 *
 * <p>换掉 uni.showModal 与 uni.showActionSheet：系统弹窗是平台默认样式的白框，
 * 与本项目「暖橙 + 大圆角」的语言完全不搭，且在小屏上正文居中、按钮等宽，
 * 明细稍长就挤成一团。这里把四种形态收进一个单例：
 *
 * <p>为什么用模块级单例而不是每页各持一份状态：调用点在工具函数里（confirm-pay、
 * report），拿不到页面上下文；调用方只 await 一个 Promise，页面里挂一个
 * {@code CmDialogHost} 即可。**同一时刻只允许一个弹窗**，后来的请求直接返回取消，
 * 避免叠层——小程序里两个遮罩叠在一起时，点击穿透会落到错误的那一层。
 */

import { reactive } from 'vue'

export interface DialogLine {
  label?: string
  value: string
  /** 重点行：价格、金额之类需要一眼看到的 */
  strong?: boolean
}

export interface DialogOptions {
  title?: string
  content?: string
  /** 明细行：比 content 的整段文字更好扫读，价格类弹窗用它 */
  lines?: DialogLine[]
  /** 底部小字说明：口径、风险提示 */
  note?: string
  confirmText?: string
  cancelText?: string
  /** 危险动作：确认键改红，用于删除 / 取消订单 */
  danger?: boolean
  /** 输入型弹窗的占位文案与长度上限 */
  placeholder?: string
  maxlength?: number
}

type Kind = 'confirm' | 'alert' | 'prompt' | 'actions'

export interface DialogRequest extends DialogOptions {
  kind: Kind
  items?: string[]
  resolve: (value: unknown) => void
}

export const dialogState = reactive<{ current: DialogRequest | null }>({ current: null })

function open<T>(req: Omit<DialogRequest, 'resolve'>): Promise<T> {
  return new Promise<T>((resolve) => {
    if (dialogState.current) {
      // 上一个还没收：新请求按取消处理，绝不叠层
      resolve((req.kind === 'actions' ? -1 : req.kind === 'prompt' ? null : false) as T)
      return
    }
    dialogState.current = { ...req, resolve: resolve as (v: unknown) => void }
  })
}

/** 确认框：确定 true / 取消 false */
export function cmConfirm(opts: DialogOptions): Promise<boolean> {
  return open<boolean>({ kind: 'confirm', confirmText: '确定', cancelText: '取消', ...opts })
}

/** 单键提示：只有一个「知道了」 */
export function cmAlert(opts: DialogOptions): Promise<void> {
  return open<void>({ kind: 'alert', confirmText: '知道了', ...opts }).then(() => undefined)
}

/** 输入框：确定返回文本（可能为空串），取消返回 null */
export function cmPrompt(opts: DialogOptions): Promise<string | null> {
  return open<string | null>({ kind: 'prompt', confirmText: '确定', cancelText: '取消', ...opts })
}

/** 动作菜单：返回命中下标，取消 -1 */
export function cmActions(title: string, items: string[]): Promise<number> {
  return open<number>({ kind: 'actions', title, items })
}

/** 由宿主组件回填结果并关闭；未在展示中的请求忽略 */
export function settle(value: unknown): void {
  const req = dialogState.current
  if (!req) return
  dialogState.current = null
  req.resolve(value)
}

/**
 * 管理端登录与权限接口（对应后端 AuthController / PermDemoController）
 *
 * <p>id 一律 string：后端雪花 ID 已全局转字符串，定义成 number 会在订单/商品上
 * 精度丢失（>2^53 尾数变 00）。
 */

import { request } from './http'

export interface AdminLoginReq {
  username: string
  password: string
  /** 验证码 key；后端开关关闭时可为空 */
  captchaKey?: string
  captchaCode?: string
}

/** 邮箱验证码登录入参（与密码登录并列，不合并——凭据形态不同：长期密码 vs 一次性码） */
export interface AdminMailLoginReq {
  email: string
  /** 邮箱收到的 6 位数字验证码 */
  mailCode: string
  captchaKey?: string
  captchaCode?: string
}

export interface AdminLoginVO {
  adminId: string
  realName: string
  roleCode: string
  tokenName: string
  tokenValue: string
  /** 该角色持有的权限点，与 @campus/shared 的 PERM_CODES 同名 */
  permCodes: string[]
}

export interface AdminInfo {
  adminId: string
  permCodes: string[]
}

/* ── 注册 / 找回密码（business/03 F-A0 / F-A10）───────────── */

export interface CaptchaVO {
  /** 提交表单时原样带回；验证码文本本身永不下发到前端 */
  captchaKey: string
  /** PNG base64，不含 data: 前缀，前端自行拼接 */
  captchaImage: string
  /** 后端开关关闭时为 true：此时不显示验证码输入框，也不必带参 */
  disabled?: boolean
}

export interface AdminRegisterReq {
  email: string
  /** 邮箱收到的 6 位数字验证码 */
  mailCode: string
  password: string
  realName?: string
  captchaKey?: string
  captchaCode?: string
}

/**
 * 邮箱验证码场景，与后端 `MailCodeScene` 的 code 一一对应（改值要同步改后端）。
 *
 * 三场景的发码规则完全一致，只有「是否暴露邮箱已注册」和邮件文案不同。
 */
export const MAIL_CODE_SCENE = {
  register: 1,
  reset: 2,
  login: 3
} as const

export type MailCodeScene = (typeof MAIL_CODE_SCENE)[keyof typeof MAIL_CODE_SCENE]

export interface AdminSendCodeReq {
  /** 见 MAIL_CODE_SCENE：1 注册 / 2 找回密码 / 3 登录 */
  type: MailCodeScene
  email: string
  captchaKey?: string
  captchaCode?: string
}

export interface AdminResetReq {
  email: string
  mailCode: string
  password: string
}

export const authApi = {
  /** 登录：账号 + 密码（+ 图形验证码）。失败 5 次锁定 10 分钟 */
  login: (req: AdminLoginReq) =>
    request<AdminLoginVO>({ url: '/admin/auth/login', method: 'POST', data: req }),

  /** 邮箱验证码登录第二步：返回的令牌、权限点与密码登录完全一致 */
  loginByMailCode: (req: AdminMailLoginReq) =>
    request<AdminLoginVO>({ url: '/admin/auth/login/mail', method: 'POST', data: req }),

  logout: () => request<void>({ url: '/admin/auth/logout', method: 'POST' }),

  /** 图形验证码：登录 / 注册 / 找回密码三处共用，一次性 */
  captcha: () => request<CaptchaVO>({ url: '/admin/auth/captcha' }),

  /**
   * 发邮箱验证码（注册 / 找回密码 / 登录 三场景共用一个端点，用 type 区分）。
   * 60 秒按邮箱节流、10 分钟有效、一次性；type=1 时邮箱已注册会报 4008，
   * type=2/3 对未注册邮箱同样返回成功（后端防邮箱枚举）。
   */
  sendCode: (req: AdminSendCodeReq) =>
    request<void>({ url: '/admin/auth/send-code', method: 'POST', data: req }),

  /** 注册第二步：提交后为「待分配角色」，需超管分配后才能登录 */
  register: (req: AdminRegisterReq) =>
    request<void>({ url: '/admin/auth/register', method: 'POST', data: req }),

  /** 找回密码第二步：成功后该账号所有会话被踢下线 */
  resetPassword: (req: AdminResetReq) =>
    request<void>({ url: '/admin/auth/forgot/reset', method: 'POST', data: req }),

  /** 当前登录人信息，用于刷新令牌与权限点 */
  info: () => request<AdminInfo>({ url: '/admin/auth/info' }),

  /** 权限点全量清单（A9 权限矩阵的行） */
  permCodes: () => request<string[]>({ url: '/admin/auth/perm-codes' })
}

/** 权限隔离演示（可验证证据：审核员调 ban 会返回 1002） */
export const demoApi = {
  /** 需要 review.judge —— 审核员及以上 */
  review: () => request<{ passed: string }>({ url: '/admin/demo/review' }),
  /** 需要 user.ban —— 仅超级管理员；审核员调用会被后端拦下 */
  ban: () => request<{ passed: string }>({ url: '/admin/demo/ban' }),
  /** 当前账号是否持有这两个点 */
  mine: () => request<{ hasReviewJudge: boolean; hasUserBan: boolean }>({ url: '/admin/demo/mine' })
}

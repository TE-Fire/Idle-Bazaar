/**
 * 学生端认证（/api/auth）：注册、验证码登录（手机号 / QQ 邮箱）、密码登录、找回密码。
 *
 * <p>与管理端 `auth.ts` 分立：两端账号体系不同（app_user / admin_user）、
 * 令牌名不同（satoken-user / satoken-admin）。登录成功后调 `setToken`
 * 把令牌写进请求层，后续请求自动带 `satoken-user` 头。
 *
 * <p>验证码只有一套口径（notes/25）：60 秒节流、10 分钟有效、一次性、错 5 次作废。
 * 演示期短信与邮件验证码都随响应回显（devCode），上线前关掉两个 echo 开关即为生产形态。
 */

import { request } from './http'
// 场景枚举与管理端是同一个后端枚举（MailCodeSceneEnum），复用 auth.ts 那一份，
// 各自再定义一遍会在 index.ts 的 export * 里撞名。
import type { MailCodeScene } from './auth'

/** 登录 / 当前用户响应。userId 是雪花 ID 转的字符串 */
export interface UserLoginVO {
  userId: string
  nickname?: string | null
  avatarUrl?: string | null
  /** 认证：0未认证 1审核中 2已认证 3未通过 */
  authStatus: number
  creditScore: number
  /** 手机号与邮箱是本人可见的账号信息，用于「找回密码」预填 */
  phone?: string | null
  email?: string | null
  /** 是否已设密码；false 时老用户只能用验证码登录，需先走找回密码设一个 */
  hasPassword?: boolean
  /** 仅登录响应有：令牌字段名与值 */
  tokenName?: string
  tokenValue?: string
}

/** 验证码下发响应 */
export interface VerifyCodeVO {
  /** 距下次可重发的秒数（60），前端做倒计时 */
  resendAfter: number
  /** 1 短信 2 QQ 邮箱，与 CODE_CHANNEL 同源 */
  channel: number
  /** 演示期回显的验证码；生产环境为 null */
  devCode?: string | null
}

/** 验证码渠道 */
export const CODE_CHANNEL = {
  sms: 1,
  mail: 2
} as const

/** 登录方式，与后端约定一致 */
export const USER_LOGIN_TYPE = {
  code: 1,
  password: 2
} as const

export type UserLoginType = (typeof USER_LOGIN_TYPE)[keyof typeof USER_LOGIN_TYPE]

export interface UserRegisterReq {
  phone: string
  /** 只接受 QQ 邮箱（@qq.com / @foxmail.com） */
  email: string
  nickname: string
  /** 8–32 位且不含空格 */
  password: string
  /** 邮箱收到的 6 位验证码 */
  mailCode: string
}

export interface UserResetPasswordReq {
  /** 手机号或 QQ 邮箱，决定用哪个渠道的验证码 */
  account: string
  code: string
  password: string
}

/** 资料编辑请求：两个字段都可选，至少传一个；字段不传 = 不修改 */
export interface UserProfileUpdateReq {
  nickname?: string
  avatarUrl?: string
}

export const userAuthApi = {
  /**
   * 发验证码。`account` 是手机号（走短信）或 QQ 邮箱（走邮件）。
   * 60 秒内重复发返回 4010 / 4016；注册场景传手机号返回 4042。
   */
  sendCode: (account: string, scene: MailCodeScene) =>
    request<VerifyCodeVO>({ url: '/api/auth/send-code', method: 'POST', data: { account, scene } }),

  /**
   * 登录。type=1 为验证码登录（account 决定渠道），type=2 为密码登录。
   * 未注册 4015/4035，密码错误 4037，未设密码 4038，错 5 次锁 10 分钟 4039。
   */
  login: (account: string, type: UserLoginType, credential: string) =>
    request<UserLoginVO>({ url: '/api/auth/login', method: 'POST', data: { account, type, credential } }),

  /** 注册：成功后直接返回令牌（邮箱已验证，不必再输一次验证码） */
  register: (req: UserRegisterReq) =>
    request<UserLoginVO>({ url: '/api/auth/register', method: 'POST', data: { ...req } }),

  /** 找回密码：成功后该账号所有会话下线，需要用新密码或验证码重新登录 */
  resetPassword: (req: UserResetPasswordReq) =>
    request<void>({ url: '/api/auth/reset-password', method: 'POST', data: { ...req } }),

  /** 当前登录人；未登录 1001（由 http 层统一跳登录） */
  info: () => request<UserLoginVO>({ url: '/api/auth/info' }),

  /** 编辑昵称 / 头像（局部更新）；昵称不合法 4020，头像地址不合法 4021 */
  updateProfile: (req: UserProfileUpdateReq) =>
    request<UserLoginVO>({ url: '/api/auth/profile', method: 'PUT', data: { ...req } }),

  logout: () => request<void>({ url: '/api/auth/logout', method: 'POST' })
}

/* ── 学号认证（notes/20 D5）────────────────────────────── */

export interface UserAuthApplyVO {
  id: string
  userId: string
  realName: string
  studentNo: string
  collegeId: string
  credentialUrl?: string | null
  /** 1审核中 2通过 3未通过 */
  status: number
  rejectReason?: string | null
  handleTime?: string | null
  createTime: string
}

export interface UserAuthApplyReq {
  realName: string
  studentNo: string
  collegeId: string
  credentialUrl?: string
}

export const userAuthApplyApi = {
  apply: (req: UserAuthApplyReq) =>
    request<string>({ url: '/api/user/auth/apply', method: 'POST', data: { ...req } }),

  /** 最近一次申请：无申请返回 null；驳回时带 rejectReason */
  latest: () => request<UserAuthApplyVO | null>({ url: '/api/user/auth/latest' })
}

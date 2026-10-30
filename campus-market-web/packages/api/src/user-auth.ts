/**
 * 学生端登录（/api/auth，手机号 + 短信验证码）
 *
 * <p>与管理端 `auth.ts` 分立：两端账号体系不同（app_user / admin_user）、
 * 令牌名不同（satoken-user / satoken-admin）。登录成功后调 `setToken`
 * 把令牌写进请求层，后续请求自动带 `satoken-user` 头。
 *
 * <p>演示期（`campus.user.sms.echo=true`）验证码随发码响应回显，联调不用看日志；
 * 上线前关掉开关，`devCode` 即为 null。
 */

import { request } from './http'

/** 登录 / 当前用户响应。userId 是雪花 ID 转的字符串 */
export interface UserLoginVO {
  userId: string
  nickname?: string | null
  avatarUrl?: string | null
  /** 认证：0未认证 1审核中 2已认证 3未通过 */
  authStatus: number
  creditScore: number
  /** 仅登录响应有：令牌字段名与值 */
  tokenName?: string
  tokenValue?: string
}

export interface SmsCodeVO {
  /** 距下次可重发的秒数（60），前端做倒计时 */
  resendAfter: number
  /** 演示期回显的验证码；生产环境为 null */
  devCode?: string | null
}

/** 资料编辑请求：两个字段都可选，至少传一个；字段不传 = 不修改 */
export interface UserProfileUpdateReq {
  nickname?: string
  avatarUrl?: string
}

export const userAuthApi = {
  /** 发验证码。60 秒内重复发返回 4016 */
  sendCode: (phone: string) =>
    request<SmsCodeVO>({ url: '/api/auth/send-code', method: 'POST', data: { phone } }),

  /** 登录。成功返回令牌与用户信息；验证码错误 4017，手机号未注册 4015 */
  login: (phone: string, code: string) =>
    request<UserLoginVO>({ url: '/api/auth/login', method: 'POST', data: { phone, code } }),

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
  /** 提交认证：已认证 4028、审核中 4029；学号唯一性在审核通过时校验 */
  apply: (req: UserAuthApplyReq) =>
    request<string>({ url: '/api/user/auth/apply', method: 'POST', data: { ...req } }),

  /** 最近一次申请：无申请返回 null，驳回时带 rejectReason */
  latest: () => request<UserAuthApplyVO | null>({ url: '/api/user/auth/latest' })
}

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

export const userAuthApi = {
  /** 发验证码。60 秒内重复发返回 4016 */
  sendCode: (phone: string) =>
    request<SmsCodeVO>({ url: '/api/auth/send-code', method: 'POST', data: { phone } }),

  /** 登录。成功返回令牌与用户信息；验证码错误 4017，手机号未注册 4015 */
  login: (phone: string, code: string) =>
    request<UserLoginVO>({ url: '/api/auth/login', method: 'POST', data: { phone, code } }),

  /** 当前登录人；未登录 1001（由 http 层统一跳登录） */
  info: () => request<UserLoginVO>({ url: '/api/auth/info' }),

  logout: () => request<void>({ url: '/api/auth/logout', method: 'POST' })
}

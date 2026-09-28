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

export const authApi = {
  /** 登录：账号 + 密码（+ 图形验证码）。失败 5 次锁定 10 分钟 */
  login: (req: AdminLoginReq) =>
    request<AdminLoginVO>({ url: '/admin/auth/login', method: 'POST', data: req }),

  logout: () => request<void>({ url: '/admin/auth/logout', method: 'POST' }),

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

/**
 * 管理端学生账号（/admin/user，business/03 A-B5 / F-A5）
 *
 * <p>与 `admin.ts` 里的管理员账号（/admin/admins）不是一回事：那边是角色分配，
 * 这边是认证审核、封禁、信用调整——操作对象是学生。
 *
 * <p>列表里的手机号是脱敏的（`138****1234`），详情才给全量。这是刻意的：
 * 列表会被截图、会投屏，详情不会。
 */

import { request, type PageResult } from './http'

/** 学生列表行：认证态与账号态是两件事，各自独立 */
export interface AdminAppUserVO {
  id: string
  nickname?: string | null
  avatarUrl?: string | null
  /** 138****1234；无手机号为空 */
  phoneMasked?: string | null
  collegeId?: string | null
  campusId?: string | null
  /** 0未认证 1审核中 2已认证 3未通过 */
  authStatus: number
  /** 0正常 1警告 2封禁 */
  status: number
  creditScore: number
  banUntil?: string | null
  lastLoginAt?: string | null
  createTime: string
}

/** 认证申请：学生端回显驳回原因、管理端队列共用 */
export interface AdminUserAuthVO {
  id: string
  userId: string
  nickname?: string | null
  phoneMasked?: string | null
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

export interface AdminCreditLogVO {
  id: string
  changeValue: number
  beforeScore: number
  afterScore: number
  reasonType: string
  remark?: string | null
  createTime: string
}

export interface AdminAppUserDetailVO extends AdminAppUserVO {
  phone?: string | null
  realName?: string | null
  studentNo?: string | null
  banReason?: string | null
  authList: AdminUserAuthVO[]
  creditLogs: AdminCreditLogVO[]
  itemCount: number
  orderCount: number
}

export interface AdminUserListParams {
  keyword?: string
  /** 0未认证 1审核中 2已认证 3未通过 */
  authStatus?: number
  /** 0正常 1警告 2封禁 */
  status?: number
  collegeId?: string
  pageNum?: number
  pageSize?: number
}

export const adminUserApi = {
  list: (params: AdminUserListParams = {}) =>
    request<PageResult<AdminAppUserVO>>({ url: '/admin/user/list', params: { ...params } }),

  detail: (id: string) => request<AdminAppUserDetailVO>({ url: `/admin/user/${id}` }),

  /** 封禁：banUntil 留空表示永久；后端会立刻踢下线 */
  ban: (id: string, data: { reason: string; banUntil?: string }) =>
    request<void>({ url: `/admin/user/${id}/ban`, method: 'POST', data }),

  unban: (id: string) => request<void>({ url: `/admin/user/${id}/unban`, method: 'POST' }),

  /** 人工调整信用分：单次 ±20 以内、理由必填 */
  adjustCredit: (id: string, data: { delta: number; reason: string }) =>
    request<void>({ url: `/admin/user/${id}/credit`, method: 'POST', data }),

  /** 待审认证队列，先到先审 */
  pendingAuth: (pageNum = 1, pageSize = 20) =>
    request<PageResult<AdminUserAuthVO>>({
      url: '/admin/user/auth/pending',
      params: { pageNum, pageSize }
    }),

  /** 认证裁决：驳回必须带原因，原因会原样展示给学生 */
  judgeAuth: (authId: string, data: { pass: boolean; rejectReason?: string }) =>
    request<void>({ url: `/admin/user/auth/${authId}/judge`, method: 'POST', data })
}

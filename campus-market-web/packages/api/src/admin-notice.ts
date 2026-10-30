/**
 * 管理端平台公告（/admin/notice，business/03 A-B13 / F-A12）
 *
 * <p>一条公告 = 一个 `batchId`。后端按接收人逐行写 `msg_message`，列表接口把这批行
 * 聚合成一行返回，所以这里的 `receiverCount` 是"写下多少行"，不是"多少人看过"。
 *
 * <p>撤回是软删收件行：学生端看不到，管理端列表里这一行仍在，`revoked` 为 true。
 */

import { request, type PageResult } from './http'

export interface AdminNoticeVO {
  batchId: string
  title?: string | null
  content?: string | null
  /** 空 = 全员 */
  campusId?: string | null
  /** 发出时写下的收件行数 */
  receiverCount: number
  /** 未撤回的收件行数 */
  aliveCount: number
  readCount: number
  revoked: boolean
  createTime: string
}

export interface NoticePublishReq {
  title?: string
  content: string
  campusId?: string
}

export const adminNoticeApi = {
  list: (pageNum = 1, pageSize = 20) =>
    request<PageResult<AdminNoticeVO>>({ url: '/admin/notice/list', params: { pageNum, pageSize } }),

  /** 发布即送达，没有定时；返回 batchId */
  publish: (data: NoticePublishReq) => request<string>({ url: '/admin/notice', method: 'POST', data }),

  /** 按批次撤回，返回软删的行数 */
  revoke: (batchId: string) =>
    request<number>({ url: `/admin/notice/${batchId}`, method: 'DELETE' })
}

/**
 * A4 用户管理的 mock 数据（business/03 A-B5 / F-A5）
 *
 * <p>三条刻意的设计，页面要能体现出来：
 *   ① **认证审核不做批量通过**——误通过会破坏校园信任机制（F-A5 判据），
 *      所以 mock 里 `authStatus = PENDING` 的只有 3 条，页面走「逐条看证件」的流程；
 *   ② 信用分初始 100、上限 120，低于 60 的商品强制人工审核（§4.5）——
 *      所以有一条 58 分的样例，用来验证「低信用」的呈现；
 *   ③ 账号状态与认证状态是**两件事**，不要合并成一列：
 *      认证没过的人照样能正常登录，封禁的人认证可能是通过的。
 */

import type { AppUser, CreditLogItem } from '@campus/shared'
import { tintFor } from './tints'

interface Seed {
  id: string
  nick: string
  realName: string
  studentNo: string
  collegeId: string
  campusId: string
  creditScore: number
  authStatus: AppUser['authStatus']
  accountStatus: AppUser['accountStatus']
  itemCount: number
  orderCount: number
  /** 注册于 N 天前 */
  daysAgo: number
  /** 最近登录于 N 小时前 */
  loginHoursAgo: number
  rejectReason?: string
}

/**
 * 时间用「距今多久」表达而不是写死日期：
 * mock 的基准时刻在 `now.ts` 里，改成相对量才能保证任何时候打开都是合理的相对时间。
 */
const isoAgo = (days: number, hours = 0): string => {
  const d = new Date('2026-09-30T10:00:00+08:00')
  d.setDate(d.getDate() - days)
  d.setHours(d.getHours() - hours)
  return d.toISOString()
}

const SEEDS: Seed[] = [
  { id: 'u1001', nick: '阿泽', realName: '陈泽宇', studentNo: '2023210101', collegeId: 'co1', campusId: 'cp1', creditScore: 108, authStatus: 'PASSED', accountStatus: 'NORMAL', itemCount: 12, orderCount: 7, daysAgo: 210, loginHoursAgo: 2 },
  { id: 'u1002', nick: '小满', realName: '林小满', studentNo: '2023210342', collegeId: 'co8', campusId: 'cp3', creditScore: 100, authStatus: 'PASSED', accountStatus: 'NORMAL', itemCount: 5, orderCount: 11, daysAgo: 188, loginHoursAgo: 9 },
  { id: 'u1003', nick: '老周', realName: '周浩然', studentNo: '2022210567', collegeId: 'co2', campusId: 'cp1', creditScore: 94, authStatus: 'PASSED', accountStatus: 'WARNED', itemCount: 21, orderCount: 3, daysAgo: 402, loginHoursAgo: 30 },
  { id: 'u1004', nick: '果冻', realName: '苏果冻', studentNo: '2024210888', collegeId: 'co10', campusId: 'cp3', creditScore: 100, authStatus: 'PENDING', accountStatus: 'NORMAL', itemCount: 2, orderCount: 0, daysAgo: 6, loginHoursAgo: 5 },
  { id: 'u1005', nick: '大熊', realName: '熊启明', studentNo: '2024210233', collegeId: 'co3', campusId: 'cp2', creditScore: 100, authStatus: 'PENDING', accountStatus: 'NORMAL', itemCount: 0, orderCount: 1, daysAgo: 3, loginHoursAgo: 1 },
  { id: 'u1006', nick: '柚子', realName: '陈柚', studentNo: '2023210777', collegeId: 'co9', campusId: 'cp3', creditScore: 100, authStatus: 'PENDING', accountStatus: 'NORMAL', itemCount: 1, orderCount: 0, daysAgo: 2, loginHoursAgo: 0 },
  { id: 'u1007', nick: '阿凯', realName: '黄凯', studentNo: '2022210119', collegeId: 'co5', campusId: 'cp2', creditScore: 58, authStatus: 'PASSED', accountStatus: 'NORMAL', itemCount: 9, orderCount: 2, daysAgo: 356, loginHoursAgo: 52 },
  { id: 'u1008', nick: '青禾', realName: '何青禾', studentNo: '2023210455', collegeId: 'co6', campusId: 'cp1', creditScore: 112, authStatus: 'PASSED', accountStatus: 'NORMAL', itemCount: 4, orderCount: 15, daysAgo: 231, loginHoursAgo: 14 },
  { id: 'u1009', nick: '胖虎', realName: '王虎', studentNo: '2022210902', collegeId: 'co4', campusId: 'cp2', creditScore: 76, authStatus: 'PASSED', accountStatus: 'BANNED', itemCount: 3, orderCount: 0, daysAgo: 388, loginHoursAgo: 240 },
  { id: 'u1010', nick: '一诺', realName: '李一诺', studentNo: '2024210512', collegeId: 'co7', campusId: 'cp1', creditScore: 100, authStatus: 'REJECTED', accountStatus: 'NORMAL', itemCount: 0, orderCount: 0, daysAgo: 12, loginHoursAgo: 20, rejectReason: '学生证照片模糊，姓名与学号无法辨认' },
  { id: 'u1011', nick: '拾柒', realName: '吴拾柒', studentNo: '2023210666', collegeId: 'co11', campusId: 'cp1', creditScore: 100, authStatus: 'NONE', accountStatus: 'NORMAL', itemCount: 1, orderCount: 4, daysAgo: 44, loginHoursAgo: 72 },
  { id: 'u1012', nick: '木木', realName: '木小菲', studentNo: '2024210147', collegeId: 'co12', campusId: 'cp4', creditScore: 100, authStatus: 'NONE', accountStatus: 'NORMAL', itemCount: 0, orderCount: 0, daysAgo: 8, loginHoursAgo: 96 },
  { id: 'u1013', nick: '阿蓝', realName: '蓝心', studentNo: '2022210333', collegeId: 'co8', campusId: 'cp3', creditScore: 88, authStatus: 'PASSED', accountStatus: 'NORMAL', itemCount: 7, orderCount: 9, daysAgo: 366, loginHoursAgo: 6 },
  { id: 'u1014', nick: '石头', realName: '石磊', studentNo: '2023210202', collegeId: 'co3', campusId: 'cp2', creditScore: 100, authStatus: 'PASSED', accountStatus: 'NORMAL', itemCount: 2, orderCount: 6, daysAgo: 150, loginHoursAgo: 3 },
  { id: 'u1015', nick: '糖糖', realName: '唐棠', studentNo: '2024210701', collegeId: 'co10', campusId: 'cp3', creditScore: 100, authStatus: 'PASSED', accountStatus: 'NORMAL', itemCount: 6, orderCount: 2, daysAgo: 21, loginHoursAgo: 11 },
  { id: 'u1016', nick: '风间', realName: '冯健', studentNo: '2022210789', collegeId: 'co2', campusId: 'cp1', creditScore: 65, authStatus: 'PASSED', accountStatus: 'WARNED', itemCount: 14, orderCount: 1, daysAgo: 399, loginHoursAgo: 18 }
]

export const USERS: AppUser[] = SEEDS.map((s) => ({
  id: s.id,
  nick: s.nick,
  realName: s.realName,
  studentNo: s.studentNo,
  collegeId: s.collegeId,
  campusId: s.campusId,
  creditScore: s.creditScore,
  authStatus: s.authStatus,
  accountStatus: s.accountStatus,
  itemCount: s.itemCount,
  orderCount: s.orderCount,
  registerAt: isoAgo(s.daysAgo),
  lastLoginAt: isoAgo(0, s.loginHoursAgo),
  tints: tintFor(s.id),
  material: { kind: '学生证', tints: tintFor(`${s.id}-card`) },
  rejectReason: s.rejectReason
}))

/** 信用流水：只给有变动的用户造几条，用于「查看详情」抽屉 */
export const CREDIT_LOGS: CreditLogItem[] = [
  { id: 'cl1', userId: 'u1001', delta: 1, after: 108, reason: '交易完成', at: isoAgo(3) },
  { id: 'cl2', userId: 'u1001', delta: 8, after: 107, reason: '连续 8 单无纠纷', at: isoAgo(40) },
  { id: 'cl3', userId: 'u1003', delta: -6, after: 94, reason: '违约单方取消', at: isoAgo(11) },
  { id: 'cl4', userId: 'u1007', delta: -15, after: 58, reason: '商品被强制下架', at: isoAgo(9) },
  { id: 'cl5', userId: 'u1007', delta: -10, after: 73, reason: '被举报成立', at: isoAgo(24) },
  { id: 'cl6', userId: 'u1007', delta: -27, after: 83, reason: '多次引流警告', at: isoAgo(60) },
  { id: 'cl7', userId: 'u1009', delta: -24, after: 76, reason: '涉违禁品强制下架', at: isoAgo(15) },
  { id: 'cl8', userId: 'u1016', delta: -10, after: 65, reason: '被举报成立', at: isoAgo(5) },
  { id: 'cl9', userId: 'u1013', delta: -5, after: 88, reason: '违约单方取消', at: isoAgo(19) },
  { id: 'cl10', userId: 'u1008', delta: 12, after: 112, reason: '交易完成 ×12', at: isoAgo(33) }
]

export const creditLogsOf = (userId: string): CreditLogItem[] =>
  CREDIT_LOGS.filter((l) => l.userId === userId).sort((a, b) => b.at.localeCompare(a.at))

export const userById = (id: string): AppUser | undefined => USERS.find((u) => u.id === id)

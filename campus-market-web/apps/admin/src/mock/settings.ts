/**
 * A9 系统设置的 mock 数据（business/03 A-B9 / F-A9）
 *
 * <p>三块内容：操作日志（log.view）、SLA 配置（settings.edit）、敏感词（settings.edit）。
 * 管理员账号走真实后端接口（`packages/api/src/admin.ts`），不在这里 mock。
 *
 * <p>SLA 默认值直接取自 business/03 F-A9 的口径：
 * 首审响应 ≤ 2h、P95 ≤ 6h、达标率 ≥ 95%、超 24h 记积压红牌、单条处置 ≤ 30s、申诉 48h。
 */

import type { OperationLogItem, SensitiveWord, SlaConfig } from '@campus/shared'

const isoAgo = (days: number, hours = 0): string => {
  const d = new Date('2026-09-30T10:00:00+08:00')
  d.setDate(d.getDate() - days)
  d.setHours(d.getHours() - hours)
  return d.toISOString()
}

/** SLA 阈值可在设置页调整，所以它是「可写」的 mock——页面改的是这份对象 */
export const DEFAULT_SLA: SlaConfig = {
  firstResponseHours: 2,
  p95Hours: 6,
  targetRate: 95,
  backlogHours: 24,
  avgHandleSeconds: 30,
  appealHours: 48
}

export const OPERATION_LOGS: OperationLogItem[] = [
  { id: 'l1', operator: '超级管理员', module: '审核', action: '强制下架', target: 'iPhone 14 128G（i2017）', basis: '校外引流（含联系方式/二维码）', ip: '10.12.33.7', at: isoAgo(0, 1) },
  { id: 'l2', operator: '审核员·李', module: '审核', action: '驳回', target: '大学物理实验报告册（i2018）', basis: '图片不实（网图）', ip: '10.12.33.21', at: isoAgo(0, 3) },
  { id: 'l3', operator: '审核员·李', module: '审核', action: '通过', target: '尤克里里 23 寸（i2022）', ip: '10.12.33.21', at: isoAgo(0, 4) },
  { id: 'l4', operator: '超级管理员', module: '用户', action: '封禁', target: '王虎（u1009）', basis: '涉违禁品强制下架累计 2 次', ip: '10.12.33.7', at: isoAgo(0, 6) },
  { id: 'l5', operator: '运营·陈', module: '内容', action: '新增分类', target: '数码电子 / 影音设备', ip: '10.12.33.44', at: isoAgo(1) },
  { id: 'l6', operator: '审核员·李', module: '申诉', action: '维持原判', target: '大学物理实验报告册（i2018）', basis: '凭证不足以推翻原判定', ip: '10.12.33.21', at: isoAgo(1, 2) },
  { id: 'l7', operator: '超级管理员', module: '用户', action: '认证通过', target: '陈泽宇（u1001）', ip: '10.12.33.7', at: isoAgo(2) },
  { id: 'l8', operator: '超级管理员', module: '用户', action: '认证驳回', target: '李一诺（u1010）', basis: '学生证照片模糊，姓名与学号无法辨认', ip: '10.12.33.7', at: isoAgo(2, 5) },
  { id: 'l9', operator: '运营·陈', module: '内容', action: '停用面交点', target: '南苑宿舍区·校门内侧', basis: '夜间照明不足，改至快递驿站旁', ip: '10.12.33.44', at: isoAgo(3) },
  { id: 'l10', operator: '审核员·李', module: '商品', action: '下架', target: '迷你洗衣机（i2020）', basis: '成色虚标，要求整改后重提', ip: '10.12.33.21', at: isoAgo(3, 4) },
  { id: 'l11', operator: '超级管理员', module: '订单', action: '纠纷裁决', target: 'CM20260929003', basis: '验货发现与描述不符，退款买家', ip: '10.12.33.7', at: isoAgo(4) },
  { id: 'l12', operator: '超级管理员', module: '系统', action: '修改 SLA 阈值', target: '首审响应 4h → 2h', ip: '10.12.33.7', at: isoAgo(5) },
  { id: 'l13', operator: '运营·陈', module: '内容', action: '新增敏感词', target: '代写', ip: '10.12.33.44', at: isoAgo(6) },
  { id: 'l14', operator: '审核员·李', module: '审核', action: '通过', target: '机械键盘 87 键（i2010）', ip: '10.12.33.21', at: isoAgo(7) },
  { id: 'l15', operator: '超级管理员', module: '用户', action: '警告', target: '周浩然（u1003）', basis: '违约单方取消 2 次', ip: '10.12.33.7', at: isoAgo(8) }
]

/** 日志模块筛选的下拉来源：从数据里派生，避免手写两遍 */
export const LOG_MODULES = Array.from(new Set(OPERATION_LOGS.map((l) => l.module)))

export const SENSITIVE_WORDS: SensitiveWord[] = [
  { id: 'w1', word: '加微信', level: '疑似', enabled: true },
  { id: 'w2', word: '代写', level: '拦截', enabled: true },
  { id: 'w3', word: '代考', level: '拦截', enabled: true },
  { id: 'w4', word: '私聊优惠', level: '疑似', enabled: true },
  { id: 'w5', word: '先款后货', level: '拦截', enabled: true },
  { id: 'w6', word: '校外', level: '疑似', enabled: false }
]

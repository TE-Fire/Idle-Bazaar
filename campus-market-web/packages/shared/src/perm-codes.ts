/**
 * 管理端权限点常量（17 个）
 *
 * <p>三方同名约定：本文件的字符串 == 后端 `PermCodes.java` 常量 == 数据库
 * `admin_role_perm.perm_code` == 原型 `data-perm`。任何一处拼错都会造成
 * 「前端隐藏了但后端没拦」的安全裂缝，改动必须四处同步。
 *
 * <p>与后端不同的唯一一处：后端用 `ALL` 数组做启动校验，这里用 `as const`
 * 让 TS 推导出字面量联合类型，菜单与指令里写错点会被编译期拦下。
 */

export const PERM_CODES = {
  /* ── 看板与报表 ── */
  DASHBOARD_VIEW: 'dashboard.view',
  REPORT_VIEW: 'report.view',

  /* ── 商品 ── */
  ITEM_VIEW: 'item.view',
  ITEM_OFFLINE: 'item.offline',
  ITEM_DELETE: 'item.delete',

  /* ── 用户 ── */
  USER_VIEW: 'user.view',
  USER_VERIFY: 'user.verify',
  USER_BAN: 'user.ban',

  /* ── 审核 ── */
  /** 进审核队列页、看队列与详情的只读点；四个角色都有（与 item.view 同一模式） */
  REVIEW_VIEW: 'review.view',
  REVIEW_JUDGE: 'review.judge',
  REVIEW_FORCE_OFF: 'review.forceoff',
  REVIEW_APPEAL: 'review.appeal',

  /* ── 订单 ── */
  /** 进订单页、看列表与详情的只读点；四个角色都有（只读角色巡盘也要能看订单） */
  ORDER_VIEW: 'order.view',
  ORDER_REFUND: 'order.refund',
  ORDER_ARBITRATE: 'order.arbitrate',

  /* ── 内容与系统 ── */
  CONTENT_EDIT: 'content.edit',
  SETTINGS_EDIT: 'settings.edit',
  LOG_VIEW: 'log.view'
} as const

/** 单个权限点的字面量联合类型：'dashboard.view' | 'report.view' | ... */
export type PermCode = (typeof PERM_CODES)[keyof typeof PERM_CODES]

/** 全部权限点，用于权限矩阵页全量展示与前端自检 */
export const ALL_PERM_CODES = Object.values(PERM_CODES) as PermCode[]

/** 权限点中文名：菜单/按钮的 hover 提示与 A9 权限矩阵的行头 */
export const PERM_LABELS: Record<PermCode, string> = {
  [PERM_CODES.DASHBOARD_VIEW]: '查看数据看板',
  [PERM_CODES.REPORT_VIEW]: '查看数据报表',
  [PERM_CODES.ITEM_VIEW]: '查看商品',
  [PERM_CODES.ITEM_OFFLINE]: '下架商品',
  [PERM_CODES.ITEM_DELETE]: '删除商品',
  [PERM_CODES.USER_VIEW]: '查看用户',
  [PERM_CODES.USER_VERIFY]: '学号认证审核',
  [PERM_CODES.USER_BAN]: '封禁用户',
  [PERM_CODES.REVIEW_VIEW]: '查看审核队列',
  [PERM_CODES.REVIEW_JUDGE]: '审核判定',
  [PERM_CODES.REVIEW_FORCE_OFF]: '强制下架',
  [PERM_CODES.REVIEW_APPEAL]: '申诉复核',
  [PERM_CODES.ORDER_VIEW]: '查看订单',
  [PERM_CODES.ORDER_REFUND]: '订单退款',
  [PERM_CODES.ORDER_ARBITRATE]: '纠纷仲裁',
  [PERM_CODES.CONTENT_EDIT]: '内容维护',
  [PERM_CODES.SETTINGS_EDIT]: '系统设置',
  [PERM_CODES.LOG_VIEW]: '查看操作日志'
}

/** 权限点分组：与原型侧边菜单分组一致（看板 / 商品 / 用户 / 审核 / 订单 / 系统） */
export const PERM_GROUPS: ReadonlyArray<{ group: string; codes: PermCode[] }> = [
  { group: '看板与报表', codes: [PERM_CODES.DASHBOARD_VIEW, PERM_CODES.REPORT_VIEW] },
  {
    group: '商品',
    codes: [PERM_CODES.ITEM_VIEW, PERM_CODES.ITEM_OFFLINE, PERM_CODES.ITEM_DELETE]
  },
  {
    group: '用户',
    codes: [PERM_CODES.USER_VIEW, PERM_CODES.USER_VERIFY, PERM_CODES.USER_BAN]
  },
  {
    group: '审核',
    codes: [
      PERM_CODES.REVIEW_VIEW,
      PERM_CODES.REVIEW_JUDGE,
      PERM_CODES.REVIEW_FORCE_OFF,
      PERM_CODES.REVIEW_APPEAL
    ]
  },
  {
    group: '订单',
    codes: [PERM_CODES.ORDER_VIEW, PERM_CODES.ORDER_REFUND, PERM_CODES.ORDER_ARBITRATE]
  },
  {
    group: '内容与系统',
    codes: [PERM_CODES.CONTENT_EDIT, PERM_CODES.SETTINGS_EDIT, PERM_CODES.LOG_VIEW]
  }
]

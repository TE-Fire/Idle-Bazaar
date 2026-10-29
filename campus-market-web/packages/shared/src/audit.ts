/**
 * 内容审核态（三端共用，对应后端 `ContentAuditStatusEnum`）
 *
 * <p>作用于 `user_moment` / `moment_comment` / `item_comment` 三张表的 `audit_status`。
 * 与审核**单据**状态（`item_audit.status`：1 待审 / 2 通过 / 3 驳回 / 4 强制下架）不是一回事——
 * 两边都有「2」，含义正好相反，判断时别串。
 *
 * <p>屏蔽(1) 只有作者自己可见，卡片打「仅自己可见」角标；下架(2) 对任何人不可见。
 */

export const CONTENT_AUDIT_STATUS = {
  NORMAL: 0,
  MASKED: 1,
  OFFLINE: 2
} as const

export type ContentAuditStatus = (typeof CONTENT_AUDIT_STATUS)[keyof typeof CONTENT_AUDIT_STATUS]

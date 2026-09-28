/**
 * 管理端角色码（与 `admin_role.role_code`、后端种子数据同名）
 *
 * <p>注意后端种子里是**大写**（SUPER/REVIEWER/OPERATOR/READONLY），
 * 前端不要顺手转小写，否则角色徽章对不上。
 */

export const ROLE_CODES = {
  SUPER: 'SUPER',
  REVIEWER: 'REVIEWER',
  OPERATOR: 'OPERATOR',
  READONLY: 'READONLY'
} as const

export type RoleCode = (typeof ROLE_CODES)[keyof typeof ROLE_CODES]

/** 角色中文名 + Tag 配色（取 design/03 状态色，不用 AntD 默认色板） */
export const ROLE_META: Record<RoleCode, { label: string; color: string }> = {
  SUPER: { label: '超级管理员', color: 'red' },
  REVIEWER: { label: '审核员', color: 'blue' },
  OPERATOR: { label: '运营', color: 'orange' },
  READONLY: { label: '只读', color: 'default' }
}

/**
 * 后端统一演示密码（四个角色相同）。
 * 只用于登录页的「一键填充」与 README，别当成生产配置。
 */
export const DEMO_PASSWORD = 'Campus@2026'

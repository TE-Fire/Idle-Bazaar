/**
 * 演示账号清单（seed：db/zz-demo-data.sql 的 app_user 3001–3012）
 *
 * <p>仅用于本地联调：登录走「手机号 + 短信验证码」，dev 期验证码随发码响应回显
 * （campus.user.sms.echo=true）。切换账号时前端自动 send-code → 取 devCode → login，
 * 无需手填验证码。字段与 app_user 种子一一对应。
 *
 * <p>authStatus：0未认证 1审核中 2已认证 3未通过；status=1 的账号（3008）处于封禁态，
 * 登录会被后端拒绝，保留它便于测封禁流程。
 */

export interface DemoUser {
  id: number
  nickname: string
  phone: string
  /** 认证：0未认证 1审核中 2已认证 3未通过 */
  authStatus: number
  creditScore: number
  /** 是否封禁（app_user.status=1）；封禁账号登录会被后端拒 */
  banned?: boolean
}

export const DEMO_USERS: DemoUser[] = [
  { id: 3001, nickname: '小林同学', phone: '13810003001', authStatus: 2, creditScore: 92 },
  { id: 3002, nickname: '阿泽', phone: '13810003002', authStatus: 2, creditScore: 101 },
  { id: 3003, nickname: '研三学姐', phone: '13810003003', authStatus: 2, creditScore: 106 },
  { id: 3004, nickname: '老周', phone: '13810003004', authStatus: 2, creditScore: 112 },
  { id: 3005, nickname: '小满', phone: '13810003005', authStatus: 2, creditScore: 106 },
  { id: 3006, nickname: '阿凯', phone: '13810003006', authStatus: 2, creditScore: 94 },
  { id: 3007, nickname: '橙子', phone: '13810003007', authStatus: 2, creditScore: 110 },
  { id: 3008, nickname: '老张', phone: '13810003008', authStatus: 2, creditScore: 109, banned: true },
  { id: 3009, nickname: '小鹿', phone: '13810003009', authStatus: 2, creditScore: 97 },
  { id: 3010, nickname: '阿岩', phone: '13810003010', authStatus: 2, creditScore: 100 },
  { id: 3011, nickname: '彤彤', phone: '13810003011', authStatus: 1, creditScore: 112 },
  { id: 3012, nickname: '大熊', phone: '13810003012', authStatus: 0, creditScore: 95 }
]

/** authStatus → 中文标签（下拉里直观区分认证态） */
export function authLabel(authStatus: number): string {
  switch (authStatus) {
    case 0:
      return '未认证'
    case 1:
      return '审核中'
    case 2:
      return '已认证'
    case 3:
      return '未通过'
    default:
      return '未知'
  }
}

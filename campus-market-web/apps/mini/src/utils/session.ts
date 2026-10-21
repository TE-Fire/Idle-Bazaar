/**
 * 会话存储（uni.storage）
 *
 * <p>为什么不用 pinia：小程序端只需要「令牌 + 登录人」两个字段，
 * 且刷新（冷启动）后要从 storage 恢复——一个模块足够，引 store 是杀鸡用牛刀。
 * 令牌由登录响应下发（tokenName=satoken-user），请求层从同一会话里取。
 */

const SESSION_KEY = 'campus-user-session'

interface Session {
  userId: string
  nickname: string
  tokenName: string
  tokenValue: string
}

let session: Session = { userId: '', nickname: '', tokenName: '', tokenValue: '' }

/** 启动时恢复；storage 读取是同步 API，放在模块顶层即可 */
try {
  const raw = uni.getStorageSync(SESSION_KEY)
  if (raw) session = { ...session, ...(JSON.parse(raw) as Session) }
} catch {
  uni.removeStorageSync(SESSION_KEY)
}

export function getTokenName(): string {
  return session.tokenName
}

export function getTokenValue(): string {
  return session.tokenValue
}

export function getSession(): Session {
  return session
}

export function saveSession(next: Partial<Session>): void {
  session = { ...session, ...next }
  uni.setStorageSync(SESSION_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  session = { userId: '', nickname: '', tokenName: '', tokenValue: '' }
  uni.removeStorageSync(SESSION_KEY)
}

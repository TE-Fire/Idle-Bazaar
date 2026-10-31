/**
 * 本机新注册账号清单（测试用）
 *
 * <p>注册成功后由注册页写入浏览器 / 小程序本地存储，测试账号切换面板单独列一组
 * 「刚注册」，省得为了看一眼新账号又去翻手机号手输。只存在本机，不进种子数据、
 * 也不上传——换设备或清缓存就没了，这是刻意的：它记录的是「本机刚造过的账号」。
 *
 * <p>之所以不是后端的「测试账号表」：这份清单只服务于本地联调，落库反而要额外
 * 开一套读写接口与权限，收益不成比例。
 */

export interface TestAccount {
  /** app_user.id（雪花 ID 字符串），与登录响应一致，用于去重与高亮 */
  id: string
  nickname: string
  phone: string
  email: string
  /** 认证：0未认证 1审核中 2已认证 3未通过；新注册恒为 0 */
  authStatus: number
  creditScore: number
  /** 注册时间戳（毫秒），列表按它倒序 */
  createdAt: number
}

export const TEST_ACCOUNTS_KEY = 'campus-test-accounts'

/** 只留最近若干条：这是顺手记的清单，不是通讯录 */
export const TEST_ACCOUNT_LIMIT = 8

/** 插入或更新（同 id 覆盖），新的排在最前，超出上限的旧记录丢弃 */
export function upsertTestAccount(list: TestAccount[], item: TestAccount): TestAccount[] {
  const rest = list.filter((u) => u.id !== item.id && u.phone !== item.phone)
  return [item, ...rest].slice(0, TEST_ACCOUNT_LIMIT)
}

export function removeTestAccount(list: TestAccount[], id: string): TestAccount[] {
  return list.filter((u) => u.id !== id)
}

/** 读回来的东西可能是旧版本或被手改过，字段缺失的整条丢掉，别让面板崩 */
export function parseTestAccounts(raw: unknown): TestAccount[] {
  if (typeof raw !== 'string' || !raw) {
    return []
  }
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
      .filter((u): u is TestAccount => !!u && typeof u === 'object')
      .filter((u) => typeof u.id === 'string' && typeof u.phone === 'string' && typeof u.nickname === 'string')
      .map((u) => ({
        id: u.id,
        nickname: u.nickname,
        phone: u.phone,
        email: typeof u.email === 'string' ? u.email : '',
        authStatus: typeof u.authStatus === 'number' ? u.authStatus : 0,
        creditScore: typeof u.creditScore === 'number' ? u.creditScore : 100,
        createdAt: typeof u.createdAt === 'number' ? u.createdAt : 0
      }))
      .sort((a, b) => b.createdAt - a.createdAt)
      .slice(0, TEST_ACCOUNT_LIMIT)
  } catch {
    return []
  }
}

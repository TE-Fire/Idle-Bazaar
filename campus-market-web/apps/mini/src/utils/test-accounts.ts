/**
 * 新注册账号的本地记录（uni.storage）
 *
 * <p>与 Web 端 utils/test-accounts 同构，只换存储实现：小程序没有 localStorage。
 * 读写包 try——存储异常不该把注册流程带崩。
 */

import type { UserLoginVO } from '@/api/user'
import {
  TEST_ACCOUNTS_KEY,
  parseTestAccounts,
  removeTestAccount,
  upsertTestAccount,
  type TestAccount
} from '@campus/shared'

export function listTestAccounts(): TestAccount[] {
  try {
    return parseTestAccounts(uni.getStorageSync(TEST_ACCOUNTS_KEY))
  } catch {
    return []
  }
}

function save(list: TestAccount[]): void {
  try {
    uni.setStorageSync(TEST_ACCOUNTS_KEY, JSON.stringify(list))
  } catch {
    // 存不下就算了：清单是便利功能，不是业务数据
  }
}

/** 注册成功即记一条；没有手机号无法用验证码切换，干脆不记 */
export function rememberRegisteredUser(vo: UserLoginVO): void {
  const phone = (vo.phone ?? '').trim()
  if (!vo.userId || !phone) {
    return
  }
  save(
    upsertTestAccount(listTestAccounts(), {
      id: vo.userId,
      nickname: vo.nickname || phone,
      phone,
      email: vo.email ?? '',
      authStatus: vo.authStatus ?? 0,
      creditScore: vo.creditScore ?? 100,
      createdAt: Date.now()
    })
  )
}

export function forgetTestAccount(id: string): void {
  save(removeTestAccount(listTestAccounts(), id))
}

/**
 * 新注册账号的本地记录（localStorage）
 *
 * <p>读写在 try 里：隐私模式或配额满时 localStorage 会抛异常，「记个测试清单」不该
 * 影响注册主流程，失败就静默跳过（下次注册再试）。
 */

import type { UserLoginVO } from '@campus/api'
import {
  TEST_ACCOUNTS_KEY,
  parseTestAccounts,
  removeTestAccount,
  upsertTestAccount,
  type TestAccount
} from '@campus/shared'

export function listTestAccounts(): TestAccount[] {
  try {
    return parseTestAccounts(window.localStorage.getItem(TEST_ACCOUNTS_KEY))
  } catch {
    return []
  }
}

function save(list: TestAccount[]): void {
  try {
    window.localStorage.setItem(TEST_ACCOUNTS_KEY, JSON.stringify(list))
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
      nickname: vo.nickname ?? phone,
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

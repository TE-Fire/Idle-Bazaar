/**
 * 管理端会话 store（Pinia setup 写法）
 *
 * <p>持久化只存「令牌 + 登录人 + 权限点」，不存任何业务列表——
 * 缓存列表会导致售出/审核状态不更新（tech/02 §6.3）。
 *
 * <p>这里手写 localStorage 而没用 pinia-plugin-persistedstate：
 * 需要持久化的就三个字段，插件是 1 个依赖换 6 行代码，等真的要持久化
 * 第二个 store 时再引不迟。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { authApi, setToken, clearToken } from '@campus/api'
import type { AdminLoginReq } from '@campus/api'
import type { PermCode } from '@campus/shared'

const STORAGE_KEY = 'campus-admin-session'

interface Session {
  adminId: string
  realName: string
  roleCode: string
  tokenName: string
  tokenValue: string
  permCodes: PermCode[]
}

export const useUserStore = defineStore('user', () => {
  const adminId = ref('')
  const realName = ref('')
  const roleCode = ref('')
  const tokenName = ref('')
  const tokenValue = ref('')
  const permCodes = ref<PermCode[]>([])

  /** 刷新页面后恢复会话；数据损坏时静默清空，不要抛错阻塞启动 */
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const s = JSON.parse(raw) as Session
      adminId.value = s.adminId ?? ''
      realName.value = s.realName ?? ''
      roleCode.value = s.roleCode ?? ''
      tokenName.value = s.tokenName ?? ''
      tokenValue.value = s.tokenValue ?? ''
      permCodes.value = s.permCodes ?? []
      if (tokenName.value && tokenValue.value) setToken(tokenName.value, tokenValue.value)
    }
  } catch {
    localStorage.removeItem(STORAGE_KEY)
  }

  const isLogin = computed(() => Boolean(tokenValue.value))

  const persist = (): void => {
    const s: Session = {
      adminId: adminId.value,
      realName: realName.value,
      roleCode: roleCode.value,
      tokenName: tokenName.value,
      tokenValue: tokenValue.value,
      permCodes: permCodes.value
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(s))
  }

  /** 登录：后端下发 tokenName（管理端为 satoken-admin），写回请求层 */
  const login = async (req: AdminLoginReq): Promise<void> => {
    const vo = await authApi.login(req)
    adminId.value = vo.adminId
    realName.value = vo.realName
    roleCode.value = vo.roleCode
    tokenName.value = vo.tokenName
    tokenValue.value = vo.tokenValue
    permCodes.value = (vo.permCodes ?? []) as PermCode[]
    setToken(vo.tokenName, vo.tokenValue)
    persist()
  }

  /** 登出：先调后端注销，再清本地——后端失败也要清，否则界面卡在半登录态 */
  const logout = async (): Promise<void> => {
    try {
      await authApi.logout()
    } finally {
      clearSession()
    }
  }

  const clearSession = (): void => {
    adminId.value = ''
    realName.value = ''
    roleCode.value = ''
    tokenName.value = ''
    tokenValue.value = ''
    permCodes.value = []
    clearToken()
    localStorage.removeItem(STORAGE_KEY)
  }

  /** 按钮级与菜单级权限判定共用这一处 */
  const hasPerm = (code: PermCode): boolean => permCodes.value.includes(code)

  /** 刷新权限点：角色被改权限后不重新登录也能生效 */
  const refreshPerms = async (): Promise<void> => {
    const info = await authApi.info()
    permCodes.value = (info.permCodes ?? []) as PermCode[]
    persist()
  }

  return {
    adminId,
    realName,
    roleCode,
    permCodes,
    isLogin,
    login,
    logout,
    clearSession,
    hasPerm,
    refreshPerms
  }
})

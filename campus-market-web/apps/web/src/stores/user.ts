/**
 * 学生端会话 store（Pinia setup 写法，与管理端 stores/user.ts 同构）
 *
 * <p>持久化只存「令牌 + 登录人」：刷新页面后会话不丢，也不缓存任何业务列表
 * （缓存列表会导致商品状态不更新）。
 *
 * <p>登录走手机号 + 短信验证码（后端 `/api/auth/login`），令牌名由后端下发
 * （satoken-user，7 天）。登出先调后端再清本地——后端失败也要清，
 * 否则界面卡在半登录态。
 */

import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { userAuthApi, setToken, clearToken, type UserLoginVO, type UserProfileUpdateReq } from '@campus/api'

const STORAGE_KEY = 'campus-user-session'

interface Session {
  userId: string
  nickname: string
  avatarUrl: string
  authStatus: number
  creditScore: number
  tokenName: string
  tokenValue: string
}

export const useUserStore = defineStore('user', () => {
  const userId = ref('')
  const nickname = ref('')
  const avatarUrl = ref('')
  const authStatus = ref(0)
  const creditScore = ref(100)
  const tokenName = ref('')
  const tokenValue = ref('')

  /** 刷新页面后恢复会话；数据损坏时静默清空，不抛错阻塞启动 */
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const s = JSON.parse(raw) as Session
      userId.value = s.userId ?? ''
      nickname.value = s.nickname ?? ''
      avatarUrl.value = s.avatarUrl ?? ''
      authStatus.value = s.authStatus ?? 0
      creditScore.value = s.creditScore ?? 100
      tokenName.value = s.tokenName ?? ''
      tokenValue.value = s.tokenValue ?? ''
      if (tokenName.value && tokenValue.value) setToken(tokenName.value, tokenValue.value)
    }
  } catch {
    localStorage.removeItem(STORAGE_KEY)
  }

  const isLogin = computed(() => Boolean(tokenValue.value))

  const persist = (): void => {
    const s: Session = {
      userId: userId.value,
      nickname: nickname.value,
      avatarUrl: avatarUrl.value,
      authStatus: authStatus.value,
      creditScore: creditScore.value,
      tokenName: tokenName.value,
      tokenValue: tokenValue.value
    }
    localStorage.setItem(STORAGE_KEY, JSON.stringify(s))
  }

  /** 登录成功后会话落地：只认后端 VO，令牌名与值都由它下发 */
  const applySession = (vo: UserLoginVO): void => {
    userId.value = vo.userId
    nickname.value = vo.nickname ?? ''
    avatarUrl.value = vo.avatarUrl ?? ''
    authStatus.value = vo.authStatus ?? 0
    creditScore.value = vo.creditScore ?? 100
    if (vo.tokenName && vo.tokenValue) {
      tokenName.value = vo.tokenName
      tokenValue.value = vo.tokenValue
      setToken(vo.tokenName, vo.tokenValue)
    }
    persist()
  }

  /** 登录：手机号 + 短信验证码 */
  const login = async (phone: string, code: string): Promise<void> => {
    applySession(await userAuthApi.login(phone, code))
  }

  /**
   * 编辑资料（昵称 / 头像，notes/20 D3）。
   * 响应不带令牌字段，applySession 不会动会话令牌，只刷新资料四字段。
   */
  const updateProfile = async (req: UserProfileUpdateReq): Promise<void> => {
    applySession(await userAuthApi.updateProfile(req))
  }

  /**
   * 重新拉一次登录人信息（认证状态、信用分等会变）。
   * 用于学号认证提交后刷新 authStatus，避免顶栏「已认证」标识还停在旧值。
   */
  const refresh = async (): Promise<void> => {
    applySession(await userAuthApi.info())
  }

  const logout = async (): Promise<void> => {
    try {
      await userAuthApi.logout()
    } finally {
      clearSession()
    }
  }

  const clearSession = (): void => {
    userId.value = ''
    nickname.value = ''
    avatarUrl.value = ''
    authStatus.value = 0
    creditScore.value = 100
    tokenName.value = ''
    tokenValue.value = ''
    clearToken()
    localStorage.removeItem(STORAGE_KEY)
  }

  return {
    userId,
    nickname,
    avatarUrl,
    authStatus,
    creditScore,
    isLogin,
    login,
    updateProfile,
    refresh,
    logout,
    clearSession
  }
})

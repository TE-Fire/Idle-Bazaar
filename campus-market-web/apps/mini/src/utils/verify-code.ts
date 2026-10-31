/**
 * 验证码发送与倒计时（小程序登录 / 注册 / 找回密码三页共用）
 *
 * <p>三页发码规则完全一致，差别只在 scene。倒计时以响应里的 `resendAfter`
 * 为准，不写死 60——前后端各存一个常数迟早对不上。
 *
 * <p>账号形态决定渠道（手机号→短信、QQ 邮箱→邮件），由后端判定并回 channel；
 * 前端只粗筛「是不是个能发的账号」，细规则交给后端，避免两套口径。
 */

import { computed, ref } from 'vue'
import { onUnload } from '@dcloudio/uni-app'
import { userAuthApi, type MailCodeScene } from '@/api/user'

/** 手机号：11 位、1 开头、第二位 3-9（与后端 UserSmsService 同规则） */
export const PHONE_RE = /^1[3-9]\d{9}$/

/** QQ 系邮箱：@qq.com / @foxmail.com，大小写不敏感 */
export const MAIL_RE = /^[^\s@]+@(qq|foxmail)\.com$/i

export type AccountKind = 'phone' | 'mail'

/** 判定账号形态；两者都不是时返回 null（页面据此禁用发码） */
export function accountKind(account: string): AccountKind | null {
  const v = (account || '').trim()
  if (PHONE_RE.test(v)) return 'phone'
  if (MAIL_RE.test(v)) return 'mail'
  return null
}

export function useCodeSender(scene: MailCodeScene) {
  const cooldown = ref(0)
  const counting = computed(() => cooldown.value > 0)
  let timer: number | null = null

  function stop(): void {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
  }

  /** 发码：成功 true；失败 false（提示已由 request 层按错误码弹出，冷却不启动） */
  async function send(account: string): Promise<boolean> {
    if (!accountKind(account) || counting.value) return false
    try {
      const vo = await userAuthApi.sendCode(account.trim(), scene)
      uni.showToast({
        title: vo.devCode ? `演示验证码：${vo.devCode}` : '验证码已发送',
        icon: 'none',
        duration: 2500
      })
      stop()
      cooldown.value = vo.resendAfter || 60
      timer = setInterval(() => {
        cooldown.value -= 1
        if (cooldown.value <= 0) stop()
      }, 1000) as unknown as number
      return true
    } catch {
      return false
    }
  }

  // 页面卸载必须停表，否则倒计时继续跑并持有旧页面引用
  onUnload(stop)

  return { cooldown, counting, send, stop }
}

/**
 * 验证码发送与倒计时（登录 / 注册 / 找回密码三页共用）
 *
 * <p>三页的发码规则完全一致，差别只在 scene。倒计时秒数以响应里的
 * `resendAfter` 为准而不是写死 60——前后端各存一份 60 迟早对不上。
 *
 * <p>账号形态决定渠道：手机号走短信、QQ 邮箱走邮件，由后端判定并回 `channel`。
 * 前端这里只做「是不是个能发的账号」的粗筛，细规则（是否已注册、是否 QQ 系）
 * 交给后端，前端重复实现必然出现两套口径。
 */

import { computed, onBeforeUnmount, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { userAuthApi, type MailCodeScene } from '@campus/api'

/** 手机号：11 位、1 开头、第二位 3-9（与后端 UserSmsService 同规则） */
export const PHONE_RE = /^1[3-9]\d{9}$/

/** QQ 系邮箱：@qq.com / @foxmail.com，大小写不敏感 */
export const MAIL_RE = /^[^\s@]+@(qq|foxmail)\.com$/i

export type AccountKind = 'phone' | 'mail'

/** 判定账号形态；不是手机也不是 QQ 邮箱时返回 null（页面据此禁用发码按钮） */
export function accountKind(account: string): AccountKind | null {
  const v = account.trim()
  if (PHONE_RE.test(v)) return 'phone'
  if (MAIL_RE.test(v)) return 'mail'
  return null
}

export function useVerifyCode(scene: MailCodeScene) {
  const cooldown = ref(0)
  const sending = ref(false)
  let timer: ReturnType<typeof setInterval> | null = null

  const counting = computed(() => cooldown.value > 0)

  function stop(): void {
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }

  /**
   * 发码。成功返回 true，失败返回 false（错误提示由 http 层统一弹，
   * 这里不重复；冷却也不启动，用户可立刻改正后重试）。
   */
  async function send(account: string): Promise<boolean> {
    if (!accountKind(account) || counting.value || sending.value) return false
    sending.value = true
    try {
      const vo = await userAuthApi.sendCode(account.trim(), scene)
      // 演示期回显：没接短信商 / 邮件服务不真发，验证码在响应里
      ElMessage.success({
        message: vo.devCode
          ? `演示验证码：${vo.devCode}（${vo.channel === 2 ? '邮箱' : '短信'}）`
          : '验证码已发送，请查收',
        duration: 4000
      })
      cooldown.value = vo.resendAfter || 60
      stop()
      timer = setInterval(() => {
        cooldown.value -= 1
        if (cooldown.value <= 0) stop()
      }, 1000)
      return true
    } catch {
      return false
    } finally {
      sending.value = false
    }
  }

  /** 页面卸载必须调：定时器挂在 window 上，不清会一直跑到 0 */
  onBeforeUnmount(stop)

  return { cooldown, counting, sending, send, stop }
}

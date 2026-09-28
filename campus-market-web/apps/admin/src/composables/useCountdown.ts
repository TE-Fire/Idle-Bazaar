import { onUnmounted, ref } from 'vue'

/**
 * 发送验证码后的 60 秒倒计时（A0 注册 / A10 找回密码共用）
 *
 * <p>为什么前端也要倒计时：后端有 60 秒节流（错误码 4010），
 * 但那是**被拦之后的报错**——用户点了没反应再弹红字体验很差。
 * 前端先禁用按钮，把「不能点」这件事在点击前就表达出来。
 *
 * <p>组件卸载时必须清定时器：路由切走后 interval 还在跑会去改已卸载的 ref。
 */
export function useCountdown(seconds = 60) {
  const left = ref(0)
  let timer: ReturnType<typeof setInterval> | undefined

  const stop = (): void => {
    if (timer) clearInterval(timer)
    timer = undefined
    left.value = 0
  }

  const start = (): void => {
    left.value = seconds
    if (timer) clearInterval(timer)
    timer = setInterval(() => {
      left.value -= 1
      if (left.value <= 0) stop()
    }, 1000)
  }

  onUnmounted(stop)

  return { left, start, stop }
}

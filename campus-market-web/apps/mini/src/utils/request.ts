/**
 * uni.request 封装：Result 解包 + 令牌头 + 业务码处理
 *
 * <p>与 Web / 管理端（@campus/api 的 axios 层）同一套口径：
 * ① 业务成功码是 0（不是 HTTP 200），登录失效是 HTTP 200 + 业务码 1001，判定看 body；
 * ② 拦截后业务层拿到的直接是 data，不用层层 .data.data；
 * ③ 令牌以「与 tokenName 同名的请求头」携带（Sa-Token 约定）。
 *
 * <p>小程序是独立工程（vite 版本锁死，不进 pnpm workspace），不引 @campus/api，
 * 所以这里用 uni.request 复刻同一份语义，而不是把 axios 拖进 uni 运行时。
 */

import { getTokenName, getTokenValue, clearSession } from './session'

interface Result<T> {
  code: number
  msg: string
  data: T
}

interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: Record<string, unknown>
  /**
   * query 参数。**后端 `@RequestParam` 收的入参必须走这里，不能塞 data**——
   * data 走的是 body，参数位置不对后端会 400，异常又被全局兜成 code:5000，
   * 表现为「点了没反应」（核销码传 body 就踩过这个坑）。
   */
  params?: Record<string, unknown>
  /** 静默失败：不弹 toast，由调用方自己决定怎么降级（如联调账号列表） */
  silent?: boolean
}

/** 1001：清会话并跳登录（带 redirect，登录后回原页） */
function goLogin(): void {
  clearSession()
  const pages = getCurrentPages()
  const current = pages[pages.length - 1]
  // route 不带前导斜杠；redirectTo 只认绝对路径，这里补齐。别再 encodeURIComponent，
  // navigateTo 自己会编一次，编两次会变成 %252F 解不回来。
  const redirect = current && current.route ? `?redirect=/${current.route}` : ''
  uni.reLaunch({ url: `/pages/login/index${redirect}` })
}

/** query 序列化：跳过 undefined / null，其余统一 encodeURIComponent */
function toQueryString(params: Record<string, unknown>): string {
  return Object.entries(params)
    .filter(([, v]) => v !== undefined && v !== null)
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join('&')
}

export function request<T>(options: RequestOptions): Promise<T> {
  const tokenName = getTokenName()
  const tokenValue = getTokenValue()
  // GET 的 uni.request 会把 data 拼到 query 上，但 POST 不会——POST 的 data 是 body。
  // 所以 query 一律由 params 显式拼进 url，避免「同一个接口换 method 就丢参数」。
  const url = options.params
    ? `${options.url}${options.url.includes('?') ? '&' : '?'}${toQueryString(options.params)}`
    : options.url
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url,
      method: options.method ?? 'GET',
      data: options.data,
      header: tokenName && tokenValue ? { [tokenName]: tokenValue } : {},
      success: (res) => {
        const body = res.data as Result<T>
        if (body && body.code === 0) {
          resolve(body.data)
          return
        }
        if (body && body.code === 1001) {
          goLogin()
          reject(new Error(body.msg || '请先登录'))
          return
        }
        if (options.silent) {
          reject(new Error(body?.msg ?? '请求失败'))
          return
        }
        uni.showToast({ title: body?.msg ?? '请求失败', icon: 'none', duration: 2000 })
        reject(new Error(body?.msg ?? '请求失败'))
      },
      fail: () => {
        if (!options.silent) {
          uni.showToast({ title: '网络异常，请稍后重试', icon: 'none', duration: 2000 })
        }
        reject(new Error('network error'))
      }
    })
  })
}

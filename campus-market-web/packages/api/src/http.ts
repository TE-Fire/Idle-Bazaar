/**
 * Axios 实例与 Result 解包（tech/02 §6.1）
 *
 * <p>后端统一响应 `Result{code,msg,data,traceId}`，**业务成功码是 0**（不是 200），
 * 且登录失效返回的是 HTTP 200 + 业务码 1001，所以判定必须看 body，不能只看状态码。
 * 拦截器里解包后，业务层拿到的直接是 `data`，不用层层 `.data.data`。
 *
 * <p>本文件不引任何 UI 库：toast / 跳转登录由宿主应用通过 `configureApi` 注入，
 * 这样用户端（Element Plus）与管理端（Ant Design Vue）能共用同一份请求层。
 */

import axios, { type AxiosError, type AxiosInstance, type AxiosResponse } from 'axios'

/** 后端统一响应体；null 字段不输出，故 traceId 可选 */
export interface Result<T> {
  code: number
  msg: string
  data: T
  traceId?: string
}

/**
 * 分页响应体（对应后端 `com.campus.common.result.PageResult`）。
 *
 * <p>放在这里而不是各个业务接口文件：分页形状是全端一致的约定，
 * 商品 / 订单 / 用户 / 日志列表共用，`total` 是**总条数**不是总页数。
 */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

/** 业务错误码（与后端 CommonErrorCode 通用段一致） */
export const BIZ_CODE = {
  OK: 0,
  UNAUTHORIZED: 1001,
  FORBIDDEN: 1002
} as const

/** 宿主应用注入的副作用：解耦 UI 库 */
export interface ApiHooks {
  /** 通用错误提示（含 traceId） */
  onError?: (msg: string, traceId?: string) => void
  /** 未登录 / 登录失效：清会话并跳登录 */
  onUnauthorized?: () => void
  /** 无权限：原地提示，不跳走 */
  onForbidden?: (msg: string) => void
}

let hooks: ApiHooks = {}

/** 在应用入口调用一次，注入 toast 与跳转 */
export function configureApi(next: ApiHooks): void {
  hooks = { ...hooks, ...next }
}

/** 把 traceId 拼进提示文案：用户截图反馈后可直接拿它查后端日志 */
const withTrace = (msg: string, traceId?: string): string =>
  traceId ? `${msg}（trace: ${traceId}）` : msg

/* ── 令牌：由 store 在登录/登出时写入 ─────────────────── */

let tokenName = ''
let tokenValue = ''

/** 登录成功后写入；tokenName 由后端下发（管理端为 satoken-admin） */
export function setToken(name: string, value: string): void {
  tokenName = name
  tokenValue = value
}

export function clearToken(): void {
  tokenValue = ''
}

export const http: AxiosInstance = axios.create({
  // 走 Vite 代理，同源，避免 CORS；前缀 /admin /api 由后端区分
  baseURL: '/',
  timeout: 15_000
})

http.interceptors.request.use((config) => {
  if (tokenName && tokenValue) {
    // Sa-Token 默认从「与 tokenName 同名的请求头」取值
    config.headers[tokenName] = tokenValue
  }
  return config
})

http.interceptors.response.use(
  (res: AxiosResponse<Result<unknown>>) => {
    const body = res.data
    const traceId = body?.traceId ?? (res.headers?.['x-trace-id'] as string | undefined)

    if (body?.code === BIZ_CODE.OK) {
      // 解包：业务层只拿 data
      return body.data as unknown as AxiosResponse<Result<unknown>>
    }

    if (body?.code === BIZ_CODE.UNAUTHORIZED) {
      clearToken()
      hooks.onUnauthorized?.()
      return Promise.reject(new Error(body.msg))
    }

    if (body?.code === BIZ_CODE.FORBIDDEN) {
      hooks.onForbidden?.(body.msg)
      return Promise.reject(new Error(body.msg))
    }

    hooks.onError?.(withTrace(body?.msg ?? '请求失败', traceId))
    return Promise.reject(new Error(body?.msg ?? '请求失败'))
  },
  (err: AxiosError<Result<unknown>>) => {
    const status = err.response?.status
    if (status === 401) {
      clearToken()
      hooks.onUnauthorized?.()
      return Promise.reject(err)
    }
    hooks.onError?.(err.message || '网络异常，请稍后重试')
    return Promise.reject(err)
  }
)

/**
 * 语义化请求的统一入口：返回**解包后的 data**，不是 Result 外壳。
 *
 * <p>为什么不写 `http.request<Result<T>, T>`：axios 的返回类型是条件类型
 * `AxiosResponseResult<T, R>`，泛型函数内部的 `R` 还没落地时 TS 无法判定，
 * 只能得到「可能与 T 无关」的结论。运行期确实是 T（拦截器已解包），
 * 所以这里显式断言一次——这是本项目唯一一处合法的 `as unknown as`。
 */
export function request<T>(config: {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  params?: Record<string, unknown>
  data?: unknown
}): Promise<T> {
  return http.request({
    url: config.url,
    method: config.method ?? 'GET',
    params: config.params,
    data: config.data
  }) as unknown as Promise<T>
}

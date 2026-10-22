/**
 * 图片上传（两步票据制，tech/01 4.4 策略模式）
 *
 * <p>流程：① `ticket` 由后端策略签发票据；② 按票据 mode 分流——
 * PROXY 用 FormData POST 到服务端端点（经 {@link ./http} 的拦截器解包 Result），
 * DIRECT 用 fetch PUT 到 MinIO 签名地址（绝对 URL、无 Result 包装，不经 http 实例）。
 * 两种方式成功后都返回票据里的 `accessUrl`（公开读直拼，不过期），
 * 提交商品时进 images 的就是它。
 *
 * <p>大小与类型前端先拦一遍（少一次往返），服务端 UploadChecks 是最终判官。
 */

import { request } from './http'

/** 与后端 {@code UploadChecks} 同一口径：改这里必须同步后端 */
const MAX_SIZE = 5 * 1024 * 1024
const IMAGE_EXTS = ['jpg', 'jpeg', 'png', 'webp']

export interface UploadTicket {
  mode: 'PROXY' | 'DIRECT'
  objectKey: string
  /** proxy 是服务端相对路径（/api/file/upload），direct 是 MinIO 绝对地址 */
  uploadUrl: string
  httpMethod: 'POST' | 'PUT'
  /** proxy 的表单附带字段（objectKey）；direct 为空 */
  formFields?: Record<string, string>
  headers?: Record<string, string>
  /** 公开读 URL：提交商品时进 images */
  accessUrl: string
  /** 签名有效期秒；proxy 为 0 */
  expiresIn: number
}

/** 前端预检：返回错误文案，null 表示可传 */
export function checkImageFile(file: File): string | null {
  if (file.size > MAX_SIZE) return '图片大小需在 5MB 以内'
  const ext = file.name.split('.').pop()?.toLowerCase() ?? ''
  if (!IMAGE_EXTS.includes(ext)) return '只支持 jpg / png / webp 图片'
  return null
}

export const fileApi = {
  /**
   * 签发上传票据。mode 不传按服务端配置（campus.upload.mode）；
   * 小程序端固定传 'PROXY'——uni.uploadFile 只能发 multipart，发不了 PUT 直传。
   */
  ticket: (filename: string, size: number, contentType: string, mode?: 'PROXY' | 'DIRECT') =>
    request<UploadTicket>({
      url: '/api/file/upload-ticket',
      method: 'POST',
      data: { filename, size, contentType, mode }
    })
}

/** 选完图直接调它：上传并返回可提交的 accessUrl；任何一步失败都抛错（调用方提示并移除占位） */
export async function uploadImage(file: File): Promise<string> {
  const invalid = checkImageFile(file)
  if (invalid) throw new Error(invalid)

  const ticket = await fileApi.ticket(file.name, file.size, file.type)
  if (ticket.mode === 'PROXY') {
    const fd = new FormData()
    fd.append('file', file)
    for (const [k, v] of Object.entries(ticket.formFields ?? {})) {
      fd.append(k, v)
    }
    await request<{ objectKey: string; url: string }>({
      url: ticket.uploadUrl,
      method: 'POST',
      data: fd
    })
  } else {
    // direct：PUT 直传 MinIO。不设 Content-Type——签名时未约束，带了反而要逐字节对齐
    const res = await fetch(ticket.uploadUrl, { method: 'PUT', body: file })
    if (!res.ok) throw new Error('上传失败，请重试')
  }
  return ticket.accessUrl
}

/**
 * 小程序图片上传（两步票据制，与 Web 端 @campus/api/file.ts 同契约）
 *
 * <p>固定 PROXY：uni.uploadFile 只能发 multipart，发不了 DIRECT 的 PUT 直传——
 * 这是平台限制不是偏好，DIRECT 模式只有 Web 端可用（服务端按 mode 配置切换）。
 */

import { request } from './request'
import { getTokenName, getTokenValue } from './session'

interface UploadTicket {
  mode: string
  objectKey: string
  uploadUrl: string
  formFields?: Record<string, string>
  accessUrl: string
}

/** 上传并把临时路径换成可提交的 accessUrl；任何一步失败都抛错（调用方移除占位） */
export async function uploadImage(filePath: string, size: number): Promise<string> {
  const raw = filePath.substring(filePath.lastIndexOf('/') + 1)
  // 临时路径可能不带扩展名，兜底 .jpg（后端白名单校验以真实文件名为准）
  const filename = raw.includes('.') ? raw : `${raw}.jpg`
  const ticket = await request<UploadTicket>({
    url: '/api/file/upload-ticket',
    method: 'POST',
    data: { filename, size, mode: 'PROXY' }
  })

  await new Promise<void>((resolve, reject) => {
    const tokenName = getTokenName()
    const tokenValue = getTokenValue()
    uni.uploadFile({
      url: ticket.uploadUrl,
      filePath,
      name: 'file',
      formData: ticket.formFields ?? {},
      // 上传也要带令牌：对象名带用户前缀，服务端按当前用户校验归属
      header: tokenName && tokenValue ? { [tokenName]: tokenValue } : {},
      success: (res) => {
        if (res.statusCode !== 200) {
          reject(new Error('上传失败'))
          return
        }
        try {
          const body = JSON.parse(res.data) as { code: number; msg?: string }
          if (body.code === 0) {
            resolve()
          } else {
            reject(new Error(body.msg ?? '上传失败'))
          }
        } catch {
          reject(new Error('上传失败'))
        }
      },
      fail: () => reject(new Error('网络异常，请稍后重试'))
    })
  })
  return ticket.accessUrl
}

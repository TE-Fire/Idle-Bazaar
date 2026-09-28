/**
 * 邮箱校验（A0 注册 / A10 找回密码共用）
 *
 * <p>只允许 QQ 系（@qq.com / @foxmail.com）是 business/03「已决 2026-09-29」的结论：
 * 管理端账号要发验证码，校园场景里学生/老师手上几乎必有 QQ 邮箱，
 * 放开任意域名只会让注册接口变成垃圾邮件的转发器。
 *
 * <p>后端 `AdminAccountService.isQqMail` 是**同一条规则的第二道**——前端这层
 * 只为即时反馈，绕过它直接打接口仍会被 4007 拦下。
 */

const QQ_MAIL = /^[^\s@]+@(qq|foxmail)\.com$/i

export const isQqMail = (email: string): boolean => QQ_MAIL.test(email.trim())

/**
 * 管理员账号管理（对应后端 AdminUserController，business/03 A9）
 *
 * <p>注册只负责建档，**角色在这里分配**：后端注册接口不接受 roleId，
 * 前端也不给「选角色」的下拉——否则任何人都能给自己审核权。
 */

import { request } from './http'

export interface AdminUserVO {
  id: string
  username: string
  email: string
  realName: string
  roleId: string
  roleCode: string
  /** 1 启用 / 0 停用 / 2 待分配角色 */
  status: number
  createTime: string
}

export interface AdminRole {
  id: string
  roleCode: string
  roleName: string
  sort: number
  createTime: string
  updateTime: string
  deleted: string
}

export const adminApi = {
  /** 待分配角色的账号（注册产生，status=2） */
  pending: () => request<AdminUserVO[]>({ url: '/admin/admins/pending' }),

  /** 可分配的角色；后端已剔除 SUPER */
  roles: () => request<AdminRole[]>({ url: '/admin/admins/roles' }),

  /** 分配角色并启用该账号 */
  assignRole: (adminId: string, roleId: string) =>
    request<void>({
      url: `/admin/admins/${adminId}/role`,
      method: 'POST',
      params: { roleId }
    })
}

/**
 * 学生端字典（/api/dict，只读）
 *
 * <p>**免登录**：发布页在未登录时也要能渲染分类 / 成色 / 校区下拉。
 * 只下发启用项——停用的字典不出现在 C 端可选列表里（「停用而非删除」的前提）。
 *
 * <p>一次拿全五张表：总量几十条、改动极少，分五次请求会拖慢首屏，
 * 还会出现「分类拉到了、成色没拉到」的中间态。
 */

import { request } from './http'

/** 分类平铺结构（带 parentId），树由前端组装 */
export interface CategoryVO {
  id: string
  name: string
  parentId: string
  sort?: number | null
}

export interface GradeVO {
  code: string
  label: string
  desc?: string | null
  color?: string | null
}

export interface CampusVO {
  id: string
  name: string
  sort?: number | null
}

/** 面交点挂在校区下：选完校区再过滤出面交点 */
export interface MeetPointVO {
  id: string
  name: string
  campusId: string
  campusName?: string | null
  openHours?: string | null
}

export interface AllDictVO {
  categories: CategoryVO[]
  grades: GradeVO[]
  campuses: CampusVO[]
  meetPoints: MeetPointVO[]
  /** 学院：学号认证流程用，发布页不展示 */
  colleges: Array<{ id: string; name: string }>
}

export const userDictApi = {
  all: () => request<AllDictVO>({ url: '/api/dict/all' })
}

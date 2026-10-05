/**
 * 字典维护（对应后端 DictAdminController，business/03 F-A7，流程见 notes/07）
 *
 * <p>五组接口对应管理端「内容管理」的五个 Tab，**全部挂 `content.edit`**——
 * 连读接口也挂：管理端字典列表只给维护者看，学生端那一路走免登录的 `/api/dict/all`。
 *
 * <p>三条与后端约定的形状（联调时踩过，别改）：
 *   ① 新增与编辑是**两个接口**：新增 `POST /admin/dict/xxx`（返回新 id），
 *      编辑 `PUT /admin/dict/xxx/{id}`。改哪一条只由 URL 路径决定，body 里不带 id。
 *   ② 成色是例外：`code`（A–D）是业务主键，走 `PUT /admin/dict/grade/{code}`，语义是 upsert。
 *   ③ 启停一律 `POST /admin/dict/xxx/{定位}/status`，body 只有 `{ enabled }`；
 *      「停用一级分类连带子级」「停用校区连带面交点与学院」由后端一条 SQL 完成，前端不做级联。
 *
 * <p>可空字段一律标成**可选**（`?: string | null`）：后端 Jackson 配了 `non_null`，
 * **值为 null 的字段直接不出现在 JSON 里**（`Result.traceId` 同理，见 `http.ts`）。
 * 所以前端拿到的是 `undefined`，不是 `null`——判空必须用 `!x` 或 `x == null`，
 * 写 `x === null` 会永远不成立。2026-10-02 踩过：分类一级 `parentId === null` 组不出树，页面整张表空白。
 */

import { request } from './http'

/* ── 返回体（对齐后端 dto/resp，Long 已全局转 String） ── */

export interface DictCategoryVO {
  id: string
  name: string
  /** 一级分类为 null（JSON 里该字段缺省） */
  parentId?: string | null
  sort: number
  enabled: boolean
  /** 该分类下的在售商品数；商品模块落地前恒为 0 */
  itemCount: number
}

/** 成色固定四档，code 是业务主键 */
export type GradeCode = 'A' | 'B' | 'C' | 'D'

export interface DictGradeVO {
  code: GradeCode
  label: string
  desc?: string | null
  /** #RRGGBB，由后端下发，前端不硬编码色值 */
  color?: string | null
  enabled: boolean
}

export interface DictMeetPointVO {
  id: string
  name: string
  campusId: string
  /** 冗余校区名，列表直接展示，不必再查一次校区字典 */
  campusName?: string | null
  openHours?: string | null
  enabled: boolean
}

export interface DictCollegeVO {
  id: string
  name: string
  /** 未指定校区时为 null（JSON 里该字段缺省） */
  campusId?: string | null
  campusName?: string | null
  sort: number
  enabled: boolean
}

export interface DictCampusVO {
  id: string
  name: string
  sort: number
  enabled: boolean
}

/* ── 入参 ── */

export interface DictCategorySaveReq {
  /** 空 = 一级分类 */
  parentId?: string | null
  name: string
  icon?: string | null
  sort?: number | null
}

export interface DictGradeSaveReq {
  /**
   * A–D。**必须带上**：后端 `DictGradeSaveReq.code` 上有 `@NotBlank`，
   * 校验在 Controller 取路径参数**之前**执行，少传就是 1000。
   * 定位仍以路径 `{code}` 为准（Controller 会用它覆盖 body 里的值），调用方保证两者一致。
   */
  code: GradeCode
  /** 成色文案 */
  name: string
  description?: string | null
  /** 必须是 #RRGGBB；空串会被后端正则拦下，没填要传 null */
  color?: string | null
  sort?: number | null
}

export interface DictMeetPointSaveReq {
  name: string
  /** 必填：必须存在且启用 */
  campusId: string
  openHours?: string | null
  sort?: number | null
}

export interface DictCollegeSaveReq {
  name: string
  /** 空 = 未指定校区 */
  campusId?: string | null
  sort?: number | null
}

export interface DictCampusSaveReq {
  name: string
  sort?: number | null
}

/**
 * 启停入参：只发 `enabled`，定位走 URL 路径。
 *
 * <p>五张表共用一个类型，与后端 `DictStatusReq` 一一对应。
 */
export interface DictStatusReq {
  enabled: boolean
}

export const dictApi = {
  /* ── 分类 ── */

  /** 两级平铺（一级 parentId 为 null），含停用项；树由前端组装 */
  categories: () => request<DictCategoryVO[]>({ url: '/admin/dict/categories' }),

  createCategory: (req: DictCategorySaveReq) =>
    request<string>({ url: '/admin/dict/category', method: 'POST', data: req }),

  updateCategory: (id: string, req: DictCategorySaveReq) =>
    request<void>({ url: `/admin/dict/category/${id}`, method: 'PUT', data: req }),

  /** 停用一级分类会连带停用其下二级；启用不连带 */
  updateCategoryStatus: (id: string, enabled: boolean) =>
    request<void>({
      url: `/admin/dict/category/${id}/status`,
      method: 'POST',
      data: { enabled } satisfies DictStatusReq
    }),

  /* ── 成色 ── */

  grades: () => request<DictGradeVO[]>({ url: '/admin/dict/grades' }),

  /**
   * 按 code upsert：空库也能直接改文案，所以新增与编辑同一个接口。
   *
   * <p>`code` 由本方法塞进 body（后端强制要求，见上），业务层只传"改什么"。
   */
  saveGrade: (code: GradeCode, req: Omit<DictGradeSaveReq, 'code'>) =>
    request<void>({ url: `/admin/dict/grade/${code}`, method: 'PUT', data: { ...req, code } }),

  updateGradeStatus: (code: GradeCode, enabled: boolean) =>
    request<void>({
      url: `/admin/dict/grade/${code}/status`,
      method: 'POST',
      data: { enabled } satisfies DictStatusReq
    }),

  /* ── 面交点 ── */

  meetPoints: () => request<DictMeetPointVO[]>({ url: '/admin/dict/meet-points' }),

  createMeetPoint: (req: DictMeetPointSaveReq) =>
    request<string>({ url: '/admin/dict/meet-point', method: 'POST', data: req }),

  updateMeetPoint: (id: string, req: DictMeetPointSaveReq) =>
    request<void>({ url: `/admin/dict/meet-point/${id}`, method: 'PUT', data: req }),

  updateMeetPointStatus: (id: string, enabled: boolean) =>
    request<void>({
      url: `/admin/dict/meet-point/${id}/status`,
      method: 'POST',
      data: { enabled } satisfies DictStatusReq
    }),

  /* ── 学院 ── */

  colleges: () => request<DictCollegeVO[]>({ url: '/admin/dict/colleges' }),

  createCollege: (req: DictCollegeSaveReq) =>
    request<string>({ url: '/admin/dict/college', method: 'POST', data: req }),

  updateCollege: (id: string, req: DictCollegeSaveReq) =>
    request<void>({ url: `/admin/dict/college/${id}`, method: 'PUT', data: req }),

  updateCollegeStatus: (id: string, enabled: boolean) =>
    request<void>({
      url: `/admin/dict/college/${id}/status`,
      method: 'POST',
      data: { enabled } satisfies DictStatusReq
    }),

  /* ── 校区 ── */

  campuses: () => request<DictCampusVO[]>({ url: '/admin/dict/campuses' }),

  createCampus: (req: DictCampusSaveReq) =>
    request<string>({ url: '/admin/dict/campus', method: 'POST', data: req }),

  updateCampus: (id: string, req: DictCampusSaveReq) =>
    request<void>({ url: `/admin/dict/campus/${id}`, method: 'PUT', data: req }),

  /** 停用会连带停用该校区下的面交点与学院；启用不连带 */
  updateCampusStatus: (id: string, enabled: boolean) =>
    request<void>({
      url: `/admin/dict/campus/${id}/status`,
      method: 'POST',
      data: { enabled } satisfies DictStatusReq
    })
}

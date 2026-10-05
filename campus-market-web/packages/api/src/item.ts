/**
 * 商品管理（对应后端 ItemAdminController，business/03 A-B4，流程见 notes/08）
 *
 * <p>四条接口对应管理端「商品管理」页 `/items`：列表、详情、强制下架、删除。
 * **没有编辑接口**——business/03 §一 明确「不做替用户改价改描述」，只能强制下架不能代编辑。
 * 页面上要把这句话写出来，否则会被当成功能缺失。
 *
 * <p>两条与后端约定的形状：
 *   ① `status` 一律传**枚举名**（`ON_SALE` 等），不传数字。后端 `ItemStatusEnum` 用 `@JsonValue`
 *      输出枚举名，与前端 `AdminItem.status` 同形，两边不必各维护一份 1–7 的映射。
 *   ② `categoryId` 传**一级分类** id，命中其下所有二级分类（后端查 `dict_category.parent_id`）；
 *      传二级 id 也能用，后端会退化成精确匹配。
 *      不能沿用 mock 的 `id.startsWith(...)`——那是 mock 的 id 编码规则，雪花 ID 下恒不成立。
 *
 * <p>可空字段一律标成**可选**：后端 Jackson 配了 `non_null`，值为 null 的字段直接不出现在 JSON 里，
 * 前端拿到的是 `undefined` 而不是 `null`（判空必须 `!x`，见 `http.ts` 与 tech/02 §6.1）。
 */

import { request, type PageResult } from './http'

/* ── 返回体（对齐后端 dto/resp，Long 已全局转 String） ── */

/** 与后端 `ItemStatusEnum` 枚举名逐字一致 */
export type ItemStatus =
  | 'DRAFT'
  | 'PENDING'
  | 'ON_SALE'
  | 'OFFLINE'
  | 'SOLD'
  | 'REJECTED'
  | 'FORCED_OFFLINE'

/**
 * 金额一律是 **2 位小数的字符串**（如 `"47.12"`），不是 number。
 *
 * <p>后端 `BigDecimal2Serializer` 按 tech/01 §5.3 规则 2 输出定长字符串，
 * 前端展示直接用、不必自己格式化；要参与比较或运算时显式 `Number(x)`。
 */
export type MoneyString = string

export interface ItemListVO {
  id: string
  title: string
  /** 主图；演示数据是外链（cdn.campus.local），加载失败时前端回退成占位色块 */
  mainImage?: string | null
  categoryId: string
  categoryName?: string | null
  price: MoneyString
  /** 卖家填的划线价；与「同类目成交均价」不是一回事 */
  originalPrice?: string | null
  /** 成色 A–D */
  condition: string
  campusId: string
  campusName?: string | null
  meetPointId?: string | null
  meetPointName?: string | null
  sellerId: string
  sellerNick?: string | null
  sellerCredit?: number | null
  status: ItemStatus
  /** 发布时间 */
  publishAt: string
  /** 审核通过时间；草稿与待审核没有这个字段（non_null） */
  auditAt?: string | null
  viewCount: number
  /** 收藏数，界面上叫「想要」 */
  wantCount: number
  /** `report` 表的实时计数，不是审核权重里的进队快照 */
  reportCount: number
  /** 图片风险分 0–100 */
  riskScore: number
  /** 同类目已成交均价；该分类没有已成交订单时为 "0.00"（价格异常判定会跳过） */
  categoryAvgPrice: MoneyString
  description?: string | null
}

export interface ItemImageVO {
  id: string
  url: string
  /** 0 为主图 */
  sort: number
}

/** 详情 = 列表一行 + 图片列表。只读，没有可提交回去的字段 */
export interface ItemDetailVO extends ItemListVO {
  images: ItemImageVO[]
}

/* ── 入参 ── */

export interface ItemListParams {
  /** 标题 / 卖家昵称模糊匹配；空 = 不筛选 */
  keyword?: string
  /** 一级分类 id；空 = 不筛选 */
  categoryId?: string
  status?: ItemStatus
  campusId?: string
  pageNum?: number
  pageSize?: number
}

export interface ItemOfflineReq {
  /** 必填：处置依据要留痕，卖家申诉时看得见 */
  reason: string
  /** 补充的具体依据，选填 */
  note?: string
}

export const itemApi = {
  /** 分页列表。空值不传，后端按「不筛选」处理 */
  list: (params: ItemListParams = {}) => {
    const query: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== null && v !== '') {
        query[k] = v
      }
    }
    return request<PageResult<ItemListVO>>({ url: '/admin/item/list', params: query })
  },

  detail: (id: string) => request<ItemDetailVO>({ url: `/admin/item/${id}` }),

  /**
   * 强制下架。仅「待审核 / 在售」可下架，其余状态后端返回 1106。
   * 与审核队列共用同一个 Service 方法，处置结果一致（status → FORCED_OFFLINE + 一条审核记录）。
   */
  offline: (id: string, req: ItemOfflineReq) =>
    request<void>({ url: `/admin/item/${id}/offline`, method: 'POST', data: req }),

  /**
   * 删除（软删，商品图一并删除）。
   * 仅「草稿 / 已下架 / 审核未过 / 已强制下架」可删，在售与已售出后端返回 1107。
   */
  remove: (id: string) => request<void>({ url: `/admin/item/${id}`, method: 'DELETE' })
}

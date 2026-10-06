/**
 * 操作日志查询（对应后端 `OperationLogController`，business/03 A9 / F-A11）
 *
 * <p>**只有读**：`sys_operation_log` 是审计表，只追加不可删改，所以这里没有
 * 新增 / 编辑 / 删除——后端也没提供。页面 A9 `/settings/logs` 是**只读**的。
 *
 * <p>与访问日志的分工（business/03 F-A11）：访问日志由 Filter 无差别记录所有请求（排查用），
 * 本表由 `@OpLog` 只记有业务语义的写动作（追责用）。少任何一个，另一个都补不上。
 *
 * <p>两条与后端的形状约定：
 *   ① `module` / `opType` 传**英文码**，后端不翻译，中文标签由前端
 *      `LOG_MODULE_LABELS` / `OP_TYPE_LABELS` 维护（2026-10-06 决策）。
 *   ② 时间筛选用**日期**（`yyyy-MM-dd`），后端把起止各自展开成当天 `00:00:00` 与 `23:59:59`；
 *      前端不要自己拼时分秒——拼成 `2026-10-06 00:00:00` 后端反而绑不上。
 *
 * <p>可空字段一律标成**可选**：后端 Jackson 配了 `non_null`，值为 null 的字段
 * 直接不出现在 JSON 里，前端拿到的是 `undefined` 而不是 `null`（判空必须 `!x`，
 * 见 `http.ts` 与 tech/02 §6.1）。`beforeValue` 就是这种情况——它一期恒为空。
 */

import { request, type PageResult } from './http'

/** 一行日志（对齐后端 `dto/resp/OperationLogVO`；Long 已全局转 String） */
export interface OperationLogVO {
  id: string
  /** 操作人 id */
  adminId: string
  /** 操作人姓名；写入时刻的快照，管理员被删号后仍能还原「当时是谁」 */
  adminName?: string | null
  /** 操作时的角色码；同样是写入时刻的快照 */
  roleCode?: string | null
  /** 模块英文码：item / review / content / system */
  module: string
  /** 操作类型英文码：APPROVE / REJECT / CATEGORY_UPDATE ... */
  opType: string
  /** 业务对象 id；主键不是数字的场景（成色 A–D）没有这个字段 */
  bizId?: string | null
  /** 注解上写的中文说明，比 `opType` 的统一标签更具体 */
  description?: string | null
  /** 变更前脱敏值；一期恒为空（切面不回查旧值），JSON 里基本不会出现 */
  beforeValue?: string | null
  /** 入参的脱敏快照（JSON 字符串）；`saveDiff = false` 的接口为空 */
  afterValue?: string | null
  /** 1 成功 / 0 失败 */
  result: number
  /** 失败错误码（业务码或异常类名）；成功时为空 */
  errorCode?: string | null
  ip?: string | null
  /** User-Agent；只在详情里展示，列表不占列宽 */
  ua?: string | null
  /** 与访问日志、响应体 traceId 对齐，可从一条日志跳到整条链路 */
  traceId?: string | null
  /** 业务方法耗时（毫秒），不是请求总耗时 */
  costMs?: number | null
  /** `yyyy-MM-dd HH:mm:ss`（全局 Jackson 定的格式，不是 ISO 那种带 T 的） */
  createTime: string
}

export interface OperationLogParams {
  /** 模块英文码；空 = 全部 */
  module?: string
  /** 模糊匹配操作者姓名 / 操作类型 / 描述 */
  keyword?: string
  /** 1 成功 / 0 失败；不传 = 全部 */
  result?: number
  /** 起始日期，含当天 */
  startTime?: string
  /** 结束日期，含当天 */
  endTime?: string
  pageNum?: number
  pageSize?: number
}

export const logApi = {
  /** 分页查询。空值不传，后端按「不筛选」处理 */
  list: (params: OperationLogParams = {}) => {
    const query: Record<string, unknown> = {}
    for (const [k, v] of Object.entries(params)) {
      // result 可能是 0（筛失败），不能用真值判断，否则筛「失败」等于没筛
      if (v !== undefined && v !== null && v !== '') {
        query[k] = v
      }
    }
    return request<PageResult<OperationLogVO>>({ url: '/admin/log/list', params: query })
  }
}

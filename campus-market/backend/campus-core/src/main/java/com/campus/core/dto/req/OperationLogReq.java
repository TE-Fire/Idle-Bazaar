package com.campus.core.dto.req;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 操作日志查询条件（管理端 A9 `/settings/logs`，接口 `/admin/log/list`）。
 *
 * <p>业务口径见 business/03 F-A11：本表**只追加、不可删改**，这里的查询是只读的，
 * 也没有对应的删除接口。
 *
 * <p>`module` 传**英文码**（`item` / `review` / `content` / `system`），与落库值同形。
 * 中文标签由前端维护（2026-10-06 决策），后端只认码、不做翻译——
 * 代价是新增模块要改前端，好处是后端加一个 `module` 取值不必同步改一处枚举。
 *
 * <p>`keyword` 模糊匹配**操作者姓名 / 操作类型 / 描述**三列，对应页面筛选框上的
 * 「操作者 / 动作 / 对象」提示。不匹配 `afterValue`：那是脱敏后的 JSON，
 * 让它参与模糊匹配既慢又容易把脱敏内容当检索词猜出来。
 *
 * <p>`startTime` / `endTime` 是**日期**（`yyyy-MM-dd`）而不是时刻：页面给的是日期区间，
 * Service 把起止各自展开成当天 `00:00:00` 与 `23:59:59`，免得调用方自己拼时分秒。
 * 两者可单独使用（只给开始 = 从那天起至今）；传反了由 Service 报 1000，不静默返回空表，
 * 否则会被当成「真的没有数据」。
 *
 * <p>`result` 传 `1` / `0` 分别筛成功 / 失败，不传为全部。留这个筛选项是因为
 * 「谁反复失败」正是本表存在的意义之一（F-A11），不该让人在结果列里肉眼找。
 *
 * <p>分页字段与 `ItemListReq` 同形（`pageNum` / `pageSize`），上限在 Service 里收。
 */
@Data
public class OperationLogReq {

    /** 模块英文码；空 = 全部 */
    private String module;

    /** 模糊匹配操作者姓名 / 操作类型 / 描述 */
    private String keyword;

    /** 1 成功 / 0 失败；不传 = 全部 */
    private Integer result;

    /** 起始日期，含当天 00:00:00 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startTime;

    /** 结束日期，含当天 23:59:59 */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endTime;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}

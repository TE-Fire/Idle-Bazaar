package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 审核队列的 SLA 概览（对齐前端 `SlaConfig` 里与队列相关的那几项 + 实时统计）。
 *
 * <p><b>两类指标不要混</b>（business/03 F-A9）：
 * <ul>
 *   <li>响应时效：`pending` / `due` / `overdue`，看的是队列里**还没处置**的单据等了多久；</li>
 *   <li>操作效率：`avgHandleSeconds` 是单条处置耗时的目标值，看的是已处置单据。</li>
 * </ul>
 * 混在一起会出现「处置越快、响应越慢」这种无法解释的曲线。
 *
 * <p>阈值当前是常量（与前端 mock 的 `DEFAULT_SLA` 一致），A9 系统设置落地后改为从配置读。
 */
@Data
public class ReviewSlaVO {

    /** 队列总条数 */
    private Integer pending;

    /** 等待 ≥ firstResponseHours 且 < backlogHours：要催 */
    private Integer due;

    /** 等待 ≥ backlogHours：积压红牌 */
    private Integer overdue;

    /** 首审响应达标阈值（小时） */
    private Integer firstResponseHours;

    /** 超过该时长未处置记积压红牌（小时） */
    private Integer backlogHours;

    /** 单条平均处置目标（秒） */
    private Integer avgHandleSeconds;
}

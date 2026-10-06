package com.campus.core.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 审核队列 + SLA 概览（`/admin/review/queue` 的出参）。
 *
 * <p>队列**不分页**（notes/09 问题 6）：连续审核（J / K 切换、处置完自动跳下一条）依赖
 * 「整个队列在前端手上」这个前提，分页会把游标逻辑切碎。队列规模由「待处置单据」决定，
 * 真实场景是几十到几百条；SLA 的积压红牌本身就是「队列不能太长」的约束。
 *
 * <p>SLA 阈值一并返回，而不是前端各存一份常量：阈值将来要挪到 A9 系统设置里可配，
 * 前端自己存就会出现「页面按 2 小时标红、后端按 4 小时统计」的口径分裂。
 */
@Data
public class ReviewQueueVO {

    private List<ReviewTaskVO> list;

    private ReviewSlaVO sla;
}

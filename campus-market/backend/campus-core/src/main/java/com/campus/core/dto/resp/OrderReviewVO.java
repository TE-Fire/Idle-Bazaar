package com.campus.core.dto.resp;

import lombok.Data;

import java.util.List;

/**
 * 一张订单的评价状态（`GET /api/order/{id}/review`，订单详情页用）。
 *
 * <p>`mine` 为空表示「我还没评」，前端据此显示「去评价」；`peer` 是对方给我的评价，
 * 对方没评也为空。**不做双盲**：不需要「等双方都评完才互见」的解锁状态（notes/13 O18 规则 3）。
 *
 * <p>`canReview` 与 `expireAt` 由服务端算：能不能评涉及订单状态与 30 天窗口两条判断，
 * 前端自己推会推出两个版本。
 */
@Data
public class OrderReviewVO {

    private Long orderId;

    /** 我提交的评价，未评为空 */
    private UserReviewVO mine;

    /** 对方给我的评价，未评为空 */
    private UserReviewVO peer;

    /** 我现在能不能评（订单已完成 + 未超窗口 + 未评过） */
    private Boolean canReview;

    /** 评价截止时间；不能评时为空 */
    private String expireAt;

    /** 服务端标签表：positive / negative 两组，前端不再硬编码文案 */
    private Object tagTable;
}

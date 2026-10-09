package com.campus.core.errcode;

import com.campus.common.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审核模块错误码（段位 30xx，登记于 tech/01 5.5）。
 */
@Getter
@AllArgsConstructor
public enum ReviewErrorCode implements IErrorCode {

    REVIEW_HANDLED(3001, "该单据已被其他管理员处置"),
    REVIEW_APPEAL_SAME_HANDLER(3002, "申诉复审不能由原处置人处理"),
    /**
     * 单据不存在或已软删。
     *
     * <p>与 {@link #REVIEW_HANDLED} 的区别：这个是「压根没有这条」，那个是「有但已经被人处置了」。
     * 分开报是因为排查方向完全不同——前者多半是前端传错 id，后者是并发。
     */
    REVIEW_TASK_NOT_FOUND(3003, "该单据不存在或已被删除"),
    /**
     * 商品状态与审核单据对不上（例如单据是待审核、商品却已经在售）。
     * 正常流程下不会出现，出现说明有别的入口改了商品状态——
     * 这里拦住而不是照着单据把商品改成「在售」，避免覆盖掉别人的处置结果。
     */
    REVIEW_ITEM_NOT_PENDING(3004, "商品不在待审核状态"),

    /** 3005 起是学生端提交举报 / 申诉的校验 */
    /** 同一人对同一对象已有未处理单据。不拦的话队列会被同一条投诉刷屏，把权重排序冲乱 */
    REPORT_DUPLICATE(3005, "你已举报过该对象，请等待处理结果"),
    CANNOT_REPORT_SELF(3006, "不能举报自己发布的商品"),
    /** 申诉是对「针对自己的处置」发起的，与单据无关者不可申诉 */
    APPEAL_NOT_ALLOWED(3007, "不可对该单据发起申诉，或已申诉过"),
    ORDER_NOT_PARTY(3008, "只能对与自己相关的订单发起纠纷"),
    ;

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    private final int code;
    private final String msg;
}

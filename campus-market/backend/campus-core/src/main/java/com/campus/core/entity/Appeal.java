package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 申诉（`appeal`，A-B3，优先级高于普通审核）。
 *
 * <p><b>`biz_id` 指什么由 `biz_type` 决定</b>（notes/09 问题 5 选 A）：
 * 1 商品审核 → `item.id`；2 举报 → `report.id`；3 订单纠纷 → `trade_order.id`。
 * 不能一律当成 `item.id`——那样就丢掉了「申诉的是哪一次举报处置」，
 * F-A4「原处置人不得复审自己的单」取不到原处置人。
 *
 * <p>终态不可再申诉（2 维持 / 3 撤销都直接结束）：不做「申诉的申诉」，
 * 否则争议可以无限递归，且每一轮都要换人，运营上不可行。
 */
@Data
@Accessors(chain = true)
@TableName("appeal")
public class Appeal {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 申诉人 `app_user.id`（被处置的一方：卖家为主） */
    private Long applicantId;

    /** 1商品审核 2举报 3订单纠纷 */
    private Integer bizType;

    /** 关联业务 ID，语义由 bizType 决定 */
    private Long bizId;

    private String reason;

    /** 新凭证 JSON */
    private String evidence;

    /** 见 {@link com.campus.core.enums.AppealStatusEnum} */
    private Integer status;

    /** 复审人；不得等于原处置人（F-A4） */
    private Long handlerId;

    private String handleNote;

    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

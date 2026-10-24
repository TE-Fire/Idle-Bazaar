package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 学生端提交申诉。
 *
 * <p>`bizType` 决定 `bizId` 指谁：1 商品审核处置（某条 `item_audit`）、
 * 2 举报处置（某条 `report`）、3 订单纠纷（某条 `trade_order`）。
 * 这是 `appeal` 的多态引用，取值以 {@link com.campus.core.enums.AppealBizTypeEnum} 为准。
 *
 * <p>申诉是对**针对自己的处置**发起的，因此 Service 会按 bizType 分头校验申诉人与单据的关系，
 * 而不是简单地「允许任何人对任何 id 申诉」。
 */
@Data
public class AppealCreateReq {

    /** 1 商品审核 2 举报 3 订单纠纷，见 `AppealBizTypeEnum` */
    @NotNull
    private Integer bizType;

    @NotNull
    private Long bizId;

    @NotBlank
    @Size(max = 500)
    private String reason;

    /** 新凭证，≤3 张 */
    @Size(max = 3)
    private List<String> evidence;
}

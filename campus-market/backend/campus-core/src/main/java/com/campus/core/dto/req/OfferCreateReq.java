package com.campus.core.dto.req;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 发起议价。
 *
 * <p>只传商品与出价：买家身份取自登录态，标价基准由服务端读商品当前价并快照，
 * 客户端传什么价格都不作为「原价」——否则改个请求就能把 500 元的货说成标价 50。
 */
@Data
public class OfferCreateReq {

    @NotNull
    private Long itemId;

    /** 出价：须低于标价且不低于标价的 10%，上限由服务端按商品价格校验 */
    @NotNull
    @DecimalMin(value = "0.01", message = "出价至少 0.01 元")
    private BigDecimal offerPrice;
}

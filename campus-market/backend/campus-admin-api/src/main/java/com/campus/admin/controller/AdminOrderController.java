package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.dto.req.AdminOrderQueryReq;
import com.campus.core.dto.req.AdminOrderRefundReq;
import com.campus.core.dto.req.OrderArbitrateReq;
import com.campus.core.dto.resp.AdminOrderVO;
import com.campus.core.oplog.OpLog;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.AdminOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单管理（管理端 A6 `/orders`，流程见 business/03 A-B7 / F-A6）。
 *
 * <p><b>权限</b>：列表与详情挂 `order.view`（四个角色都有，只读角色巡盘也要能看）；
 * 强制退款 `order.refund`；纠纷裁决 `order.arbitrate`。
 *
 * <p><b>裁决为什么单独开一个端点，不复用 `/admin/review/appeal/{id}/judge`</b>：
 * 那条挂的是 `review.appeal`（审核员持有），而订单介入的权限点是 `order.arbitrate`（运营持有）。
 * 让运营去调审核接口，等于把申诉裁决权一并给了他；两边都通到同一个 Service 方法，
 * 状态迁移与商品恢复只有一份实现。
 *
 * <p><b>可处置的状态被刻意收窄</b>：只有「退款中 / 纠纷中」两个悬置态能人工介入。
 * 已付款、待面交属于交易进行中，卖家未必违约；终态不该再动。状态机能被人工任意跳转是审计灾难。
 */
@Tag(name = "管理端 · 订单管理")
@RestController
@RequestMapping("/admin/order")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @Operation(summary = "订单列表",
            description = "分页；可按状态、只看纠纷、关键词（订单号 / 商品快照标题 / 买卖双方昵称）筛选。"
                    + "status 传枚举名（WAIT_PAY/WAIT_MEET/VERIFIED/COMPLETED/CANCELED/REFUNDING/REFUNDED/DISPUTING"
                    + "/WAIT_SHIP/SHIPPED/WAIT_PICKUP）。")
    @GetMapping("/list")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.ORDER_VIEW)
    public Result<PageResult<AdminOrderVO>> list(AdminOrderQueryReq req) {
        return Result.ok(adminOrderService.list(req));
    }

    @Operation(summary = "订单详情",
            description = "含下单时的商品快照（标题 / 价格 / 成色 / 描述）与当前商品值，以及 snapshotDrifted 漂移标记；"
                    + "纠纷单另带申诉单号、理由与发起方。不输出核销码。")
    @GetMapping("/{id}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.ORDER_VIEW)
    public Result<AdminOrderVO> detail(@PathVariable("id") Long id) {
        return Result.ok(adminOrderService.detail(id));
    }

    @Operation(summary = "强制退款",
            description = "仅「退款中 / 纠纷中」。转已退款、商品恢复在售并重置占位标记；"
                    + "若挂着待复审纠纷单，一并结案。")
    @PostMapping("/{id}/refund")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.ORDER_REFUND)
    @OpLog(module = "order", type = "REFUND_JUDGE", bizId = "#id", desc = "管理端强制退款", saveDiff = true)
    public Result<Void> refund(@PathVariable("id") Long id,
                               @RequestBody(required = false) AdminOrderRefundReq req) {
        adminOrderService.forceRefund(id, req);
        return Result.ok();
    }

    @Operation(summary = "纠纷裁决",
            description = "仅「纠纷中」。result：REFUND_BUYER（退款买家→已退款）/ PAY_SELLER（放款卖家→已完成）。"
                    + "不做部分退款——订单没有部分退终态，写了无法落库（business/03 F-A6 本期口径）。")
    @PostMapping("/{id}/arbitrate")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.ORDER_ARBITRATE)
    @OpLog(module = "order", type = "DISPUTE_JUDGE", bizId = "#id", desc = "纠纷裁决", saveDiff = true)
    public Result<Void> arbitrate(@PathVariable("id") Long id,
                                  @RequestBody OrderArbitrateReq req) {
        adminOrderService.arbitrate(id, req);
        return Result.ok();
    }
}

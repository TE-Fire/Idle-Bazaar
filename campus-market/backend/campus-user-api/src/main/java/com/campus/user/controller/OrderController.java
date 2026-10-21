package com.campus.user.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.OrderCancelReq;
import com.campus.core.dto.req.OrderCreateReq;
import com.campus.core.dto.req.OrderDisputeReq;
import com.campus.core.dto.req.RefundHandleReq;
import com.campus.core.dto.resp.OrderVO;
import com.campus.core.enums.OrderStatusEnum;
import com.campus.core.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端订单：下单、支付、核销、收货、取消与退款（清单见 `notes/13` 第七节，状态机见第二节）。
 *
 * <p><b>`role` 参数决定查哪一边的订单</b>：同一个用户既是买家又是卖家，
 * 「我买到的」与「我卖出的」走的是 `idx_buyer` / `idx_seller` 两个索引，不能合并成一个查询。
 *
 * <p><b>退款处理只有卖家能调</b>：买家出资方的心智是「申请」，卖家才是「受理」。
 * 角色判断放在 {@link OrderService#handleRefund} 里按 `seller_id` 校验，控制器不做第二遍。
 */
@Tag(name = "学生端 · 订单")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "下单", description = "只插待付款订单，商品不锁定；金额一律按标价，不可自定义")
    @PostMapping("/create")
    public Result<Long> create(@Valid @RequestBody OrderCreateReq req) {
        long userId = UserContext.getUserId();
        return Result.ok(orderService.create(userId, req.getItemId(), req.getMeetPointId(), req.getMeetTime()));
    }

    @Operation(summary = "模拟支付", description = "两级占位校验后转待面交，并批量取消同商品其余待付款订单")
    @PostMapping("/{id}/pay")
    public Result<Void> pay(@PathVariable Long id) {
        orderService.pay(id, UserContext.getUserId());
        return Result.ok();
    }

    @Operation(summary = "取消 / 申请退款", description = "待付款直接作废；已付款的取消等于发起退款申请，落退款中等卖家处理")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody(required = false) OrderCancelReq req) {
        orderService.cancel(id, UserContext.getUserId(), req == null ? null : req.getReason());
        return Result.ok();
    }

    @Operation(summary = "卖家处理退款", description = "同意 → 已退款；拒绝 → 纠纷中冻结，转管理端裁决")
    @PostMapping("/{id}/refund")
    public Result<Void> handleRefund(@PathVariable Long id, @Valid @RequestBody RefundHandleReq req) {
        orderService.handleRefund(id, UserContext.getUserId(), Boolean.TRUE.equals(req.getAgree()), req.getNote());
        return Result.ok();
    }

    @Operation(summary = "发起订单纠纷", description = "待面交/已验货/退款中可发起；订单转纠纷中冻结，同事务写申诉单进审核队列")
    @PostMapping("/{id}/dispute")
    public Result<Void> dispute(@PathVariable Long id, @RequestBody(required = false) OrderDisputeReq req) {
        orderService.openDispute(id, UserContext.getUserId(), req == null ? null : req.getReason());
        return Result.ok();
    }

    @Operation(summary = "扫码核销", description = "待面交 → 已验货；一单一张核销码，买卖任一方扫码即成立")
    @PostMapping("/{id}/verify")
    public Result<Void> verify(@PathVariable Long id, @RequestParam String code) {
        orderService.verify(id, code, UserContext.getUserId());
        return Result.ok();
    }

    @Operation(summary = "确认收货", description = "已验货 → 已完成；必须先核销，不能跳过面交环节")
    @PostMapping("/{id}/receive")
    public Result<Void> receive(@PathVariable Long id) {
        orderService.receive(id, UserContext.getUserId());
        return Result.ok();
    }

    @Operation(summary = "订单列表", description = "role=buyer 我买到的（默认），role=seller 我卖出的；status 传枚举名")
    @GetMapping("/list")
    public Result<PageResult<OrderVO>> list(@RequestParam(defaultValue = "buyer") String role,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(orderService.mine(UserContext.getUserId(), "seller".equalsIgnoreCase(role),
                parseStatus(status), pageNum, pageSize));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    public Result<OrderVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id, UserContext.getUserId()));
    }

    /** 非法状态名当「不筛选」处理：这里是列表筛选项，给它报参错会挡住整页刷新 */
    private OrderStatusEnum parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatusEnum.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}

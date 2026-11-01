package com.campus.user.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.OfferCreateReq;
import com.campus.core.dto.req.OfferRejectReq;
import com.campus.core.dto.resp.OfferVO;
import com.campus.core.service.OfferService;
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
 * 学生端议价：出价、卖家答复、买家撤回与查询（状态机见 notes/28）。
 *
 * <p><b>同一套接口服务买卖双方</b>：出价恒由买家发起，答复恒由卖家操作，
 * 角色由单据上的买家/卖家字段判定，不收客户端传的 role——传什么都不影响权限。
 *
 * <p><b>议价不改进商品价格</b>：这些接口全程不碰 `item` 表，成交价只进订单。
 */
@Tag(name = "学生端 · 议价")
@RestController
@RequestMapping("/api/offer")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    @Operation(summary = "发起议价", description = "仅可议价且在售的商品；出价须低于标价且不低于标价的 10%；同商品同时只能有一条进行中")
    @PostMapping("/create")
    public Result<Long> create(@Valid @RequestBody OfferCreateReq req) {
        return Result.ok(offerService.create(UserContext.getUserId(), req));
    }

    @Operation(summary = "卖家同意", description = "同意后买家可在 10 分钟内按该价付款，超时自动失效")
    @PostMapping("/{id}/agree")
    public Result<Void> agree(@PathVariable Long id) {
        offerService.agree(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "卖家拒绝", description = "理由可空；拒绝后可重新谈，不锁买家名额")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody(required = false) OfferRejectReq req) {
        offerService.reject(UserContext.getUserId(), id, req == null ? null : req.getReason());
        return Result.ok();
    }

    @Operation(summary = "买家撤回", description = "进行中的议价可撤回，撤回后不占名额，可立刻重新出价")
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        offerService.cancel(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "我发起的 / 我收到的", description = "role=buyer 我发出的出价（默认），role=seller 我收到的出价")
    @GetMapping("/list")
    public Result<PageResult<OfferVO>> list(@RequestParam(defaultValue = "buyer") String role,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(offerService.list(UserContext.getUserId(), "seller".equalsIgnoreCase(role),
                pageNum, pageSize));
    }

    @Operation(summary = "议价详情", description = "聊天卡片点开时取最新状态——卡片消息里的状态是发送时的快照")
    @GetMapping("/{id}")
    public Result<OfferVO> detail(@PathVariable Long id) {
        return Result.ok(offerService.detail(UserContext.getUserId(), id));
    }
}

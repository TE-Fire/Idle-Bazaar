package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.UserAddressReq;
import com.campus.core.entity.UserAddress;
import com.campus.core.service.UserAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户地址簿（收货 SHIP / 自提 PICKUP）。
 *
 * <p>`kind` 决定地址归属：收货地址由买家维护、自提地址由卖家维护，二者按当前登录人 + kind 隔离、
 * 互不可见——同一接口、同一张表，但买家永远看不到卖家的自提地址，反之亦然。
 *
 * <p>地址被订单引用后会在下单时快照进 `trade_order`，本接口只管理「可选地址簿」，
 * 不触碰任何订单（{@code UserAddressService} 注释）。
 */
@Tag(name = "学生端 · 地址")
@RestController
@RequestMapping("/api/address")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService addressService;

    @Operation(summary = "地址列表", description = "按 kind 过滤（1收货 2自提），默认地址排最前")
    @GetMapping("/list")
    public Result<List<UserAddress>> list(@RequestParam Integer kind) {
        return Result.ok(addressService.list(UserContext.getUserId(), kind));
    }

    @Operation(summary = "新增地址")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody UserAddressReq req) {
        return Result.ok(addressService.create(UserContext.getUserId(), req));
    }

    @Operation(summary = "修改地址", description = "kind 不可改，跨类型修改会被拒绝")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserAddressReq req) {
        addressService.update(UserContext.getUserId(), id, req);
        return Result.ok();
    }

    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "设为默认地址")
    @PostMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(UserContext.getUserId(), id);
        return Result.ok();
    }
}

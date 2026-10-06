package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.dto.req.ItemListReq;
import com.campus.core.dto.req.ItemOfflineReq;
import com.campus.core.dto.resp.ItemDetailVO;
import com.campus.core.dto.resp.ItemListVO;
import com.campus.core.oplog.OpLog;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.ItemAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品管理（管理端 A3 `/items`，流程见 notes/08）。
 *
 * <p><b>权限</b>：列表与详情挂 `item.view`（四个角色都有），下架 `item.offline`，删除 `item.delete`。
 * 审核员只有 `item.view`——能看不能处置，商品处置要走 A5 审核队列（持有 `review.forceoff`）。
 * 这是权限矩阵刻意设计的互斥（business/03 F-A1），不是功能缺失。
 *
 * <p><b>没有编辑接口</b>：business/03 §一明确「不做替用户改价改描述」，
 * 只能强制下架，不能代编辑，避免责任不清。
 *
 * <p><b>强制下架与审核队列共用同一个 Service 方法</b>：商品页（巡盘发现）与审核页（判读后处置）
 * 处置结果必须一致，两套实现必然漂移。权限点不同，所以两个 Controller 各挂各的注解。
 *
 * <p>写接口的 `@OpLog` 已补齐：下架 `OFF_SHELF`、删除 `DELETE`，`module = item`。
 * 业务层的处置留痕不等于 `@OpLog`——强制下架还会写一条 `item_audit`（含处置人与原因），
 * 申诉复审靠它复盘；`sys_operation_log` 是跨模块的索引，不是唯一证据。
 */
@Tag(name = "管理端 · 商品管理")
@RestController
@RequestMapping("/admin/item")
@RequiredArgsConstructor
public class ItemAdminController {

    private final ItemAdminService itemService;

    @Operation(summary = "商品列表",
            description = "分页；可按关键词（标题/卖家昵称）、一级分类、状态、校区筛选。"
                    + "categoryId 传一级分类 id 时命中其下所有二级分类；传二级 id 时精确匹配。"
                    + "status 传枚举名（DRAFT/PENDING/ON_SALE/OFFLINE/SOLD/REJECTED/FORCED_OFFLINE）。")
    @GetMapping("/list")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.ITEM_VIEW)
    public Result<PageResult<ItemListVO>> list(ItemListReq req) {
        return Result.ok(itemService.list(req));
    }

    @Operation(summary = "商品详情",
            description = "只读，含图片列表（按 sort 升序，0 为主图）。平台不提供编辑接口。")
    @GetMapping("/{id}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.ITEM_VIEW)
    public Result<ItemDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(itemService.detail(id));
    }

    @Operation(summary = "强制下架",
            description = "仅「待审核 / 在售」可下架，转「已强制下架」并写一条审核记录（处置人与原因留痕，供申诉复盘）。")
    @PostMapping("/{id}/offline")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.ITEM_OFFLINE)
    @OpLog(module = "item", type = "OFF_SHELF", bizId = "#id", desc = "强制下架", saveDiff = true)
    public Result<Void> offline(@PathVariable("id") Long id,
                                @Valid @RequestBody ItemOfflineReq req) {
        itemService.forceOffline(id, req);
        return Result.ok();
    }

    @Operation(summary = "删除商品",
            description = "软删，商品图一并删除。仅「草稿 / 已下架 / 审核未过 / 已强制下架」可删——"
                    + "已售出商品被订单引用，在售商品请先下架。")
    @DeleteMapping("/{id}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.ITEM_DELETE)
    @OpLog(module = "item", type = "DELETE", bizId = "#id", desc = "删除商品")
    public Result<Void> delete(@PathVariable("id") Long id) {
        itemService.deleteItem(id);
        return Result.ok();
    }
}

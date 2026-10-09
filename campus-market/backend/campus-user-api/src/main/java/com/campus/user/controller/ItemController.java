package com.campus.user.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.ItemListReq;
import com.campus.core.dto.req.ItemPublishReq;
import com.campus.core.dto.resp.ApiItemVO;
import com.campus.core.service.ItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端商品：发布、浏览、编辑、下架（接口清单见 `notes/13` 第七节）。
 *
 * <p><b>不挂 `@OpLog`</b>：操作日志记的是管理员行为（`AdminContext` 取不到学生），
 * 学生行为由它产生的数据本身承载——订单、举报、申诉都带着自己的时间线，不需要再抄一份日志。
 *
 * <p><b>没有登录拦截器兜底</b>：`AdminWebConfig` 只拦 `/admin/**`，这里靠
 * {@link UserContext#getUserId()} 在未登录时抛 `NotLoginException`（全局转成 1001）。
 * 浏览免登录可以省一个过滤器，写操作自然被兜住。
 */
@Tag(name = "学生端 · 商品")
@RestController
@RequestMapping("/api/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    @Operation(summary = "发布商品", description = "提交即送审：落 PENDING 并插入 item_audit 进队，不直接上架")
    @PostMapping("/publish")
    public Result<Long> publish(@Valid @RequestBody ItemPublishReq req) {
        return Result.ok(itemService.publish(UserContext.getUserId(), req));
    }

    @Operation(summary = "商品列表", description = "只返回在售商品；关键词匹配标题与描述，分类支持传一级 id")
    @GetMapping("/list")
    public Result<PageResult<ApiItemVO>> list(ItemListReq req) {
        return Result.ok(itemService.search(req));
    }

    @Operation(summary = "商品详情", description = "浏览量 +1，返回图片列表")
    @GetMapping("/{id}")
    public Result<ApiItemVO> detail(@PathVariable Long id) {
        return Result.ok(itemService.detail(id));
    }

    @Operation(summary = "编辑商品", description = "改价格或改成色须回待审核重新排队，只改描述免审")
    @PostMapping("/{id}/edit")
    public Result<Void> edit(@PathVariable Long id, @Valid @RequestBody ItemPublishReq req) {
        itemService.update(UserContext.getUserId(), id, req);
        return Result.ok();
    }

    @Operation(summary = "下架商品", description = "卖家自用下架 → OFFLINE，区别于平台处置的强制下架")
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        itemService.offline(UserContext.getUserId(), id);
        return Result.ok();
    }
}

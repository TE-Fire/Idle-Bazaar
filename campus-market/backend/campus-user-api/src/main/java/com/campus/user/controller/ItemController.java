package com.campus.user.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.ItemCommentReq;
import com.campus.core.dto.req.ItemListReq;
import com.campus.core.dto.req.ItemPublishReq;
import com.campus.core.dto.resp.ApiItemVO;
import com.campus.core.dto.resp.ItemCommentVO;
import com.campus.core.service.ItemCommentService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端商品：发布、浏览、编辑、下架（接口清单见 notes/13 第七节）。
 *
 * <p>不加 @OpLog：操作日志记管理员行为，学生行为由订单、举报、申诉自身的时间线承载。
 * 无登录拦截器兜底（AdminWebConfig 只拦 /admin/**），靠 UserContext 未登录抛
 * NotLoginException（全局转 1001）；浏览免登录，写操作自然被兜住。
 */
@Tag(name = "学生端 · 商品")
@RestController
@RequestMapping("/api/item")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final ItemCommentService itemCommentService;

    @Operation(summary = "发布商品", description = "提交即送审：落 PENDING 并插入 item_audit 进队，不直接上架；draft=true 暂存草稿，不进队、图片与描述可后补")
    @PostMapping("/publish")
    public Result<Long> publish(@Valid @RequestBody ItemPublishReq req) {
        return Result.ok(itemService.publish(UserContext.getUserId(), req));
    }

    @Operation(summary = "商品列表", description = "只返回在售商品；关键词匹配标题与描述，分类支持传一级 id")
    @GetMapping("/list")
    public Result<PageResult<ApiItemVO>> list(ItemListReq req) {
        return Result.ok(itemService.search(req));
    }

    @Operation(summary = "商品详情", description = "浏览量 +1，返回图片列表；登录态额外返回 favorited")
    @GetMapping("/{id}")
    public Result<ApiItemVO> detail(@PathVariable Long id) {
        return Result.ok(itemService.detail(id, UserContext.getUserIdOrNull()));
    }

    @Operation(summary = "编辑商品", description = "草稿可继续编辑，draft=false 时送审；已发布商品改价格或改成色回待审核，只改描述免审")
    @PostMapping("/{id}/edit")
    public Result<Void> edit(@PathVariable Long id, @Valid @RequestBody ItemPublishReq req) {
        itemService.update(UserContext.getUserId(), id, req);
        return Result.ok();
    }

    @Operation(summary = "我的商品", description = "卖家视角：含草稿等全部状态，支持状态/关键词/分类/校区筛选，按最近更新倒序")
    @GetMapping("/mine")
    public Result<PageResult<ApiItemVO>> mine(ItemListReq req) {
        return Result.ok(itemService.mine(UserContext.getUserId(), req));
    }

    @Operation(summary = "删除商品", description = "仅限草稿/已下架/审核未过（软删）；已强制下架需走申诉，在售/待审核/已售出不可删")
    @PostMapping("/{id}/delete")
    public Result<Void> delete(@PathVariable Long id) {
        itemService.delete(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "下架商品", description = "卖家自用下架 → OFFLINE，区别于平台处置的强制下架")
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        itemService.offline(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "发商品评论", description = "仅限在售商品（4025）；不限买家，内容过敏感词后入库")
    @PostMapping("/{id}/comment")
    public Result<Void> comment(@PathVariable Long id, @Valid @RequestBody ItemCommentReq req) {
        itemCommentService.publish(UserContext.getUserId(), id, req);
        return Result.ok();
    }

    @Operation(summary = "商品评论列表", description = "正序分页；下架商品仍可读历史评论")
    @GetMapping("/{id}/comments")
    public Result<PageResult<ItemCommentVO>> comments(@PathVariable Long id,
                                                      @RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(itemCommentService.list(id, pageNum, pageSize));
    }
}

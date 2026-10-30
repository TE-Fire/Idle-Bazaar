package com.campus.user.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.resp.ApiItemVO;
import com.campus.core.service.FavoriteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端收藏（notes/24）：与商品控制器同前缀但不合并——收藏是独立的关系表与独立服务。
 *
 * <p>`/api/item/favorites` 与商品的 `GET /{id}` 同层，Spring 精确路径优先于变量路径，
 * 不会被当成 id=favorites 解析；两处路径的增删仍要一起看，避免将来加了个 `/xx` 撞上变量路径。
 */
@Tag(name = "学生端 · 收藏")
@RestController
@RequestMapping("/api/item")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏", description = "幂等：已收藏再调直接返回成功")
    @PostMapping("/{id}/favorite")
    public Result<Void> add(@PathVariable Long id) {
        favoriteService.add(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "取消收藏", description = "幂等：未收藏再调直接返回成功")
    @DeleteMapping("/{id}/favorite")
    public Result<Void> remove(@PathVariable Long id) {
        favoriteService.remove(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "我的收藏", description = "收藏时间倒序；已删除与强制下架的商品不返回")
    @GetMapping("/favorites")
    public Result<PageResult<ApiItemVO>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.ok(favoriteService.list(UserContext.getUserId(), pageNum, pageSize));
    }
}

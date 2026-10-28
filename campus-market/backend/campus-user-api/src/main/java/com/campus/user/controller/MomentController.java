package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.MomentCommentReq;
import com.campus.core.dto.req.MomentPublishReq;
import com.campus.core.dto.resp.MomentDetailVO;
import com.campus.core.dto.resp.MomentPageVO;
import com.campus.core.service.MomentService;
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
 * 学生端动态（notes/22 S3–S6）：发布、广场、个人主页、详情、点赞、评论。
 *
 * <p>操作人一律取 {@link UserContext}，不接受客户端传 userId；`/user/{userId}` 只是「看谁的动态」，
 * 与操作人无关。点赞/取消点赞幂等。
 */
@Tag(name = "学生端 · 动态")
@RestController
@RequestMapping("/api/moment")
@RequiredArgsConstructor
public class MomentController {

    private final MomentService momentService;

    @Operation(summary = "发布动态", description = "type=1 纯状态 / type=2 分享在售商品；返回动态 id")
    @PostMapping("/publish")
    public Result<Long> publish(@Valid @RequestBody MomentPublishReq req) {
        return Result.ok(momentService.publish(UserContext.getUserId(), req));
    }

    @Operation(summary = "广场流", description = "全站正常动态，时间倒序")
    @GetMapping("/square")
    public Result<MomentPageVO> square(@RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(momentService.square(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "个人主页动态", description = "指定作者的正常动态，时间倒序")
    @GetMapping("/user/{userId}")
    public Result<MomentPageVO> userMoments(@PathVariable Long userId,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(momentService.userMoments(UserContext.getUserId(), userId, pageNum, pageSize));
    }

    @Operation(summary = "动态详情", description = "正文 + 评论列表（正序）")
    @GetMapping("/{id}")
    public Result<MomentDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(momentService.detail(UserContext.getUserId(), id));
    }

    @Operation(summary = "点赞", description = "幂等")
    @PostMapping("/{id}/like")
    public Result<Void> like(@PathVariable("id") Long id) {
        momentService.like(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "取消点赞", description = "幂等")
    @PostMapping("/{id}/unlike")
    public Result<Void> unlike(@PathVariable("id") Long id) {
        momentService.unlike(UserContext.getUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "发评论", description = "敏感词过滤后入库")
    @PostMapping("/{id}/comment")
    public Result<Void> comment(@PathVariable("id") Long id,
                                @Valid @RequestBody MomentCommentReq req) {
        momentService.comment(UserContext.getUserId(), id, req);
        return Result.ok();
    }
}

package com.campus.user.controller;

import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.FollowReq;
import com.campus.core.dto.resp.FollowListVO;
import com.campus.core.service.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端社交：关注 / 取关（notes/22 S1·S2）。
 *
 * <p>操作人一律取 {@link UserContext}，不接受客户端传 followerId——否则等于允许替他人关注。
 * 关注/取关都是幂等的：重复调用返回成功，不会产生第二行关系。
 */
@Tag(name = "学生端 · 社交")
@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
public class SocialController {

    private final FollowService followService;

    @Operation(summary = "关注", description = "已关注则幂等成功；不能关注自己")
    @PostMapping("/follow")
    public Result<Void> follow(@Valid @RequestBody FollowReq req) {
        followService.follow(UserContext.getUserId(), req.getFolloweeId());
        return Result.ok();
    }

    @Operation(summary = "取关", description = "未关注则幂等成功")
    @PostMapping("/unfollow")
    public Result<Void> unfollow(@Valid @RequestBody FollowReq req) {
        followService.unfollow(UserContext.getUserId(), req.getFolloweeId());
        return Result.ok();
    }

    @Operation(summary = "是否已关注", description = "个人主页关注按钮的初始态")
    @GetMapping("/follow/check")
    public Result<Boolean> check(@RequestParam(required = false) Long followeeId) {
        BizAssert.notNull(followeeId, CommonErrorCode.PARAM_ERROR, "followeeId 不能为空");
        return Result.ok(followService.isFollowing(UserContext.getUserId(), followeeId));
    }

    @Operation(summary = "关注 / 粉丝列表",
            description = "type=following 我关注的人，type=fans 我的粉丝；userId 为空看自己，传他人看其公开主页")
    @GetMapping("/follow/list")
    public Result<FollowListVO> list(@RequestParam(defaultValue = "following") String type,
                                     @RequestParam(required = false) Long userId,
                                     @RequestParam(defaultValue = "1") int pageNum,
                                     @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(followService.list(UserContext.getUserId(), userId, type, pageNum, pageSize));
    }
}

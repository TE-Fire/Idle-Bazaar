package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.UserAuthApplyReq;
import com.campus.core.dto.resp.UserAuthVO;
import com.campus.core.service.UserAuthApplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端学号认证（notes/20 D5）。
 *
 * <p>与 `/api/auth/**` 分开：那边是登录与资料，这边是认证申请——混在一起会让
 * 「账号」与「校园身份」的边界在代码里也糊掉。
 */
@Tag(name = "学生端 · 学号认证")
@RestController
@RequestMapping("/api/user/auth")
@RequiredArgsConstructor
public class UserAuthApplyController {

    private final UserAuthApplyService userAuthApplyService;

    @Operation(summary = "提交认证申请", description = "已认证或已有审核中申请会被拒（4028 / 4029）；学号唯一性在审核通过时校验")
    @PostMapping("/apply")
    public Result<Long> apply(@Valid @RequestBody UserAuthApplyReq req) {
        return Result.ok(userAuthApplyService.apply(UserContext.getUserId(), req));
    }

    @Operation(summary = "我最近一次申请", description = "无申请返回 null；驳回时带 rejectReason 供页面回显")
    @GetMapping("/latest")
    public Result<UserAuthVO> latest() {
        return Result.ok(userAuthApplyService.latest(UserContext.getUserId()));
    }
}

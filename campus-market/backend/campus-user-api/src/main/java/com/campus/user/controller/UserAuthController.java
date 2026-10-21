package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.core.dto.req.UserLoginReq;
import com.campus.core.dto.req.UserSendCodeReq;
import com.campus.core.dto.resp.SmsCodeVO;
import com.campus.core.dto.resp.UserLoginVO;
import com.campus.core.satoken.StpUserUtil;
import com.campus.core.service.UserAuthService;
import com.campus.core.service.UserSmsService;
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
 * 学生端登录与会话（business/01 F-W0 登录，手机号 + 短信验证码）。
 *
 * <p>与管理端 {@code /admin/auth} 分立：两端账号体系不同（app_user / admin_user），
 * 令牌也不同（satoken-user / satoken-admin），共用一套 Sa-Token 多账号配置。
 */
@Tag(name = "学生端 · 登录与权限")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserAuthController {

    private final UserSmsService userSmsService;
    private final UserAuthService userAuthService;

    @Operation(summary = "发短信验证码", description = "60 秒内只能发一条；演示期验证码随响应回显（devCode），生产关闭回显后只进日志")
    @PostMapping("/send-code")
    public Result<SmsCodeVO> sendCode(@Valid @RequestBody UserSendCodeReq req) {
        return Result.ok(userSmsService.sendCode(req.getPhone()));
    }

    @Operation(summary = "登录", description = "手机号 + 短信验证码；成功返回令牌（tokenName=satoken-user，7 天）与当前用户信息")
    @PostMapping("/login")
    public Result<UserLoginVO> login(@Valid @RequestBody UserLoginReq req) {
        return Result.ok(userAuthService.login(req));
    }

    @Operation(summary = "当前登录人信息", description = "刷新页面后重建会话视图用；未登录返回 1001")
    @GetMapping("/info")
    public Result<UserLoginVO> info() {
        return Result.ok(userAuthService.currentUser());
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUserUtil.logout();
        return Result.ok();
    }
}

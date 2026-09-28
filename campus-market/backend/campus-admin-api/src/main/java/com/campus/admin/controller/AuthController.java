package com.campus.admin.controller;

import com.campus.common.result.Result;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.AdminAccountService;
import com.campus.core.service.AdminAuthService;
import com.campus.core.service.CaptchaService;
import com.campus.core.service.dto.AdminLoginReq;
import com.campus.core.service.dto.AdminLoginVO;
import com.campus.core.service.dto.AdminRegisterReq;
import com.campus.core.service.dto.AdminResetReq;
import com.campus.core.service.dto.AdminSendCodeReq;
import com.campus.core.service.dto.CaptchaVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端登录与会话（business/03 F-A1，原型 A1）。
 */
@Tag(name = "管理端 · 登录与权限")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AdminAuthService adminAuthService;
    private final AdminAccountService adminAccountService;
    private final CaptchaService captchaService;

    @Operation(summary = "登录", description = "账号 + 密码（+ 图形验证码）。失败 5 次锁定 10 分钟。")
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginReq req) {
        return Result.ok(adminAuthService.login(req));
    }

    /* ── 注册 / 找回密码（business/03 F-A0 / F-A10）───────────── */

    @Operation(summary = "图形验证码", description = "登录 / 注册 / 找回密码三处共用；一次性，5 分钟有效")
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        CaptchaVO vo = captchaService.generate();
        vo.setDisabled(!captchaService.isEnabled());
        return Result.ok(vo);
    }

    @Operation(summary = "注册 · 发邮箱验证码",
            description = "邮箱已注册会直接报错（注册是公开入口，用户填了别人的邮箱需要立刻知道）")
    @PostMapping("/register/send-code")
    public Result<Void> sendRegisterCode(@Valid @RequestBody AdminSendCodeReq req) {
        adminAccountService.sendRegisterCode(req);
        return Result.ok();
    }

    @Operation(summary = "注册", description = "QQ 邮箱 + 邮箱验证码 + 图形验证码。注册后为「待分配角色」，需超管分配后才能登录")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody AdminRegisterReq req) {
        adminAccountService.register(req);
        return Result.ok();
    }

    @Operation(summary = "找回密码 · 发邮箱验证码",
            description = "邮箱未注册时也返回成功——否则该接口可用于枚举管理员邮箱")
    @PostMapping("/forgot/send-code")
    public Result<Void> sendResetCode(@Valid @RequestBody AdminSendCodeReq req) {
        adminAccountService.sendResetCode(req);
        return Result.ok();
    }

    @Operation(summary = "找回密码 · 重置", description = "校验邮箱验证码后重置密码，并强制下线该账号所有会话")
    @PostMapping("/forgot/reset")
    public Result<Void> resetPassword(@Valid @RequestBody AdminResetReq req) {
        adminAccountService.resetPassword(req);
        return Result.ok();
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout() {
        StpAdminUtil.logout();
        return Result.ok();
    }

    @Operation(summary = "当前登录人信息", description = "含该角色持有的权限点，前端据此渲染菜单与按钮")
    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        StpAdminUtil.checkLogin();
        long adminId = StpAdminUtil.getLoginIdAsLong();
        // 走 StpInterfaceImpl，从 admin_role_perm 现查，不缓存写死的清单
        List<String> perms = StpAdminUtil.stpLogic.getPermissionList();
        Map<String, Object> data = new HashMap<>();
        data.put("adminId", adminId);
        data.put("permCodes", perms);
        return Result.ok(data);
    }

    @Operation(summary = "权限点清单", description = "管理端 A9 权限矩阵的行，与 PermCodes 常量同源")
    @GetMapping("/perm-codes")
    public Result<List<String>> permCodes() {
        return Result.ok(adminAuthService.allPermCodes());
    }
}

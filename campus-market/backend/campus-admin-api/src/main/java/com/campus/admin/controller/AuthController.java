package com.campus.admin.controller;

import com.campus.common.result.Result;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.AdminAccountService;
import com.campus.core.service.AdminAuthService;
import com.campus.core.service.CaptchaService;
import com.campus.core.dto.req.AdminLoginReq;
import com.campus.core.dto.resp.AdminLoginVO;
import com.campus.core.dto.req.AdminRegisterReq;
import com.campus.core.dto.req.AdminResetReq;
import com.campus.core.dto.req.AdminSendCodeReq;
import com.campus.core.dto.resp.CaptchaVO;
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

    @Operation(summary = "登录",
            description = "type：1 用户名 + 密码 / 2 邮箱 + 邮箱验证码（验证码先调 /send-code 取）。"
                    + "两种方式的返回值完全一致（同一份 AdminLoginVO）。"
                    + "密码登录失败 5 次锁定 10 分钟；邮箱验证码登录失败**不计入**锁定——"
                    + "否则任何人拿一个已知邮箱乱猜 5 次就能把该管理员锁 10 分钟（DoS）")
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginReq req) {
        return Result.ok(adminAuthService.login(req));
    }

    /* ── 发邮箱验证码（注册 / 找回密码 / 登录 三场景共用一个端点）───── */

    @Operation(summary = "发邮箱验证码",
            description = "type：1 注册 / 2 找回密码 / 3 登录。三场景的发码规则完全一致"
                    + "（图形验证码、60 秒节流、10 分钟有效、一次性），差异只有两点："
                    + "① type=1 时邮箱已注册会返回 4008，type=2/3 对未注册邮箱同样返回成功（防枚举）；"
                    + "② 邮件文案按场景不同。"
                    + "注意节流按邮箱而非按场景，同一邮箱 60 秒内只发一封")
    @PostMapping("/send-code")
    public Result<Void> sendCode(@Valid @RequestBody AdminSendCodeReq req) {
        adminAccountService.sendCode(req);
        return Result.ok();
    }

    /* ── 注册 / 找回密码（business/03 F-A0 / F-A10）───────────── */

    @Operation(summary = "图形验证码",
            description = "登录 / 注册 / 找回密码三处共用；一次性，5 分钟有效。开关关闭时返回 disabled=true，前端隐藏输入框")
    @GetMapping("/captcha")
    public Result<CaptchaVO> captcha() {
        return Result.ok(captchaService.generate());
    }

    @Operation(summary = "注册", description = "QQ 邮箱 + 邮箱验证码 + 图形验证码。注册后为「待分配角色」，需超管分配后才能登录")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody AdminRegisterReq req) {
        adminAccountService.register(req);
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

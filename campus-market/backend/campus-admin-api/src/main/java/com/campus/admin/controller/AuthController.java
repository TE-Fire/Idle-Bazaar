package com.campus.admin.controller;

import com.campus.common.result.Result;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.AdminAuthService;
import com.campus.core.service.dto.AdminLoginReq;
import com.campus.core.service.dto.AdminLoginVO;
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

    @Operation(summary = "登录", description = "账号 + 密码（+ 图形验证码）。失败 5 次锁定 10 分钟。")
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginReq req) {
        return Result.ok(adminAuthService.login(req));
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

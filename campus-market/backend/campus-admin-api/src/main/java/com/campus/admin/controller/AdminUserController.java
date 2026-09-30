package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.Result;
import com.campus.core.entity.AdminRole;
import com.campus.core.entity.AdminUser;
import com.campus.core.mapper.AdminRoleMapper;
import com.campus.core.mapper.AdminUserMapper;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.AdminAccountService;
import com.campus.core.dto.resp.AdminUserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员账号管理（A9 系统设置 · 分配角色）。
 *
 * <p>注册只负责「建档」，**角色在这里分配**——这条分界是权限模型的安全前提：
 * 注册接口不接受 roleId，也就没人能自己给自己审核权（business/03 已决 2026-09-29）。
 */
@Tag(name = "管理端 · 管理员账号")
@RestController
@RequestMapping("/admin/admins")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminAccountService adminAccountService;

    @Operation(summary = "待分配角色的账号", description = "注册后 status=2 的账号，超管在此分配角色")
    @GetMapping("/pending")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
    public Result<List<AdminUserVO>> pending() {
        List<AdminUser> list = adminUserMapper.selectList(
                new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getStatus, 2));
        return Result.ok(list.stream().map(this::toVO).toList());
    }

    @Operation(summary = "可分配的角色", description = "不含 SUPER：超级管理员只能由开发者经种子数据创建")
    @GetMapping("/roles")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
    public Result<List<AdminRole>> assignableRoles() {
        return Result.ok(adminRoleMapper.selectList(
                new LambdaQueryWrapper<AdminRole>().ne(AdminRole::getRoleCode, "SUPER")));
    }

    @Operation(summary = "分配角色并启用", description = "把 status 置为启用；不允许分配 SUPER")
    @PostMapping("/{adminId}/role")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
    public Result<Void> assignRole(@PathVariable("adminId") Long adminId,
                                   @RequestParam("roleId") Long roleId) {
        adminAccountService.assignRole(adminId, roleId);
        return Result.ok();
    }

    private AdminUserVO toVO(AdminUser u) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(String.valueOf(u.getId()));
        vo.setUsername(u.getUsername());
        vo.setEmail(u.getEmail());
        vo.setRealName(u.getRealName());
        vo.setRoleId(String.valueOf(u.getRoleId()));
        vo.setStatus(u.getStatus());
        AdminRole role = u.getRoleId() == null ? null : adminRoleMapper.selectById(u.getRoleId());
        vo.setRoleCode(role == null ? null : role.getRoleCode());
        vo.setCreateTime(u.getCreateTime() == null ? null : u.getCreateTime().toString());
        return vo;
    }
}

package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.context.AdminContext;
import com.campus.core.dto.req.AppUserListReq;
import com.campus.core.dto.req.UserAuthJudgeReq;
import com.campus.core.dto.req.UserBanReq;
import com.campus.core.dto.req.UserCreditAdjustReq;
import com.campus.core.dto.resp.AppUserDetailVO;
import com.campus.core.dto.resp.AppUserVO;
import com.campus.core.dto.resp.UserAuthVO;
import com.campus.core.oplog.OpLog;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.AppUserAdminService;
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
 * 管理端学生账号（A4 用户管理，notes/20 D6）。
 *
 * <p>与 `AdminUserController`（`/admin/admins`）是两套东西：那边管的是管理端账号与角色，
 * 这边管的是学生账号——认证审核、封禁、信用调整。命名上刻意用 `user` 对 `admins` 区分。
 *
 * <p>权限三处：查看 `user.view`、认证审核 `user.verify`、封禁与信用调整 `user.ban`。
 * 信用调整复用 `user.ban` 不新增权限点——它和封禁同属「处置账号」这一类动作。
 */
@Tag(name = "管理端 · 学生账号")
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
public class AppUserAdminController {

    private final AppUserAdminService appUserAdminService;

    @Operation(summary = "学生列表", description = "keyword 同时匹配昵称/手机号/学号；手机号在列表里脱敏")
    @GetMapping("/list")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.USER_VIEW)
    public Result<PageResult<AppUserVO>> list(AppUserListReq req) {
        return Result.ok(appUserAdminService.list(req));
    }

    @Operation(summary = "学生详情", description = "资料 + 认证历史 + 最近 20 条信用流水 + 商品/订单计数")
    @GetMapping("/{id}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.USER_VIEW)
    public Result<AppUserDetailVO> detail(@PathVariable("id") Long id) {
        return Result.ok(appUserAdminService.detail(id));
    }

    @Operation(summary = "封禁账号", description = "写状态与原因并立即踢下线；banUntil 为空表示永久。不动信用分")
    @PostMapping("/{id}/ban")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.USER_BAN)
    @OpLog(module = "user", type = "USER_BAN", bizId = "#id", desc = "封禁学生账号", saveDiff = true)
    public Result<Void> ban(@PathVariable("id") Long id, @Valid @RequestBody UserBanReq req) {
        appUserAdminService.ban(id, req);
        return Result.ok();
    }

    @Operation(summary = "解封账号")
    @PostMapping("/{id}/unban")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.USER_BAN)
    @OpLog(module = "user", type = "USER_UNBAN", bizId = "#id", desc = "解封学生账号", saveDiff = true)
    public Result<Void> unban(@PathVariable("id") Long id) {
        appUserAdminService.unban(id);
        return Result.ok();
    }

    @Operation(summary = "人工调整信用分", description = "单次 ±20 以内、理由必填，结果夹在 0–120；写 credit_log")
    @PostMapping("/{id}/credit")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.USER_BAN)
    @OpLog(module = "user", type = "USER_CREDIT_ADJUST", bizId = "#id", desc = "人工调整信用分", saveDiff = true)
    public Result<Void> adjustCredit(@PathVariable("id") Long id,
                                     @Valid @RequestBody UserCreditAdjustReq req) {
        appUserAdminService.adjustCredit(id, req);
        return Result.ok();
    }

    @Operation(summary = "待审认证队列", description = "先到先审；带申请人昵称与脱敏手机号")
    @GetMapping("/auth/pending")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.USER_VERIFY)
    public Result<PageResult<UserAuthVO>> pendingAuth(@RequestParam(defaultValue = "1") Integer pageNum,
                                                      @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(appUserAdminService.pendingAuth(pageNum, pageSize));
    }

    @Operation(summary = "认证裁决", description = "通过时校验学号占用（4030）；驳回必须带原因，原因原样展示给学生")
    @PostMapping("/auth/{authId}/judge")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.USER_VERIFY)
    @OpLog(module = "user", type = "USER_AUTH_JUDGE", bizId = "#authId", desc = "学号认证裁决", saveDiff = true)
    public Result<Void> judgeAuth(@PathVariable("authId") Long authId,
                                  @Valid @RequestBody UserAuthJudgeReq req) {
        appUserAdminService.judgeAuth(authId, req, AdminContext.getAdminId());
        return Result.ok();
    }
}

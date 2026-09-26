package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 权限隔离演示（business/03 F-A1 的可验证证据）。
 *
 * <p>用审核员账号登录后会看到：
 * /admin/demo/review 通过（持有 review.judge），
 * /admin/demo/ban 返回 code=1002 无权限（不持有 user.ban）——
 * 这就是「审核员不能封禁用户」在后端真正生效的样子。
 */
@Tag(name = "管理端 · 权限隔离演示")
@RestController
@RequestMapping("/admin/demo")
public class PermDemoController {

    @Operation(summary = "需要 review.judge（审核员及以上）")
    @GetMapping("/review")
    @SaCheckPermission(PermCodes.REVIEW_JUDGE)
    public Result<Map<String, Object>> review() {
        return Result.ok(Map.of("passed", "review.judge"));
    }

    @Operation(summary = "需要 user.ban（仅超级管理员）")
    @GetMapping("/ban")
    @SaCheckPermission(PermCodes.USER_BAN)
    public Result<Map<String, Object>> ban() {
        return Result.ok(Map.of("passed", "user.ban"));
    }

    @Operation(summary = "当前账号持有的全部权限点")
    @GetMapping("/mine")
    public Result<Map<String, Object>> mine() {
        Map<String, Object> data = new HashMap<>();
        data.put("hasReviewJudge",
                com.campus.core.satoken.StpAdminUtil.hasPermission(PermCodes.REVIEW_JUDGE));
        data.put("hasUserBan",
                com.campus.core.satoken.StpAdminUtil.hasPermission(PermCodes.USER_BAN));
        return Result.ok(data);
    }
}

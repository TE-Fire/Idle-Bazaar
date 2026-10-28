package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.dto.req.ReportHandleReq;
import com.campus.core.dto.resp.SocialReportVO;
import com.campus.core.oplog.OpLog;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.SocialReportAdminService;
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
 * 社交举报处置（管理端，notes/22 批次 3）。
 *
 * <p><b>为什么不在审核队列</b>：队列的权重与 SLA 都是按商品设计的，动态与评论解析不出商品，
 * 混进去会把「举报次数加权」的排序规则弄脏。这里单开一条轻通道，处置动作只有「下架内容」。
 *
 * <p><b>权限</b>复用审核的两个点：查询 `review.view`、处置 `review.judge`——
 * 处置社交内容与处置商品举报是同一类判断，不新增权限点（notes/22 决策 2026-10-27）。
 */
@Tag(name = "管理端 · 社交举报")
@RestController
@RequestMapping("/admin/social/report")
@RequiredArgsConstructor
public class SocialReportAdminController {

    private final SocialReportAdminService socialReportAdminService;

    @Operation(summary = "社交举报列表",
            description = "targetType：3 动态 / 4 商品评论 / 5 动态评论；status：1 待受理 2 处理中 3 已处理")
    @GetMapping("/list")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REVIEW_VIEW)
    public Result<PageResult<SocialReportVO>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer targetType,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(socialReportAdminService.list(status, targetType, pageNum, pageSize));
    }

    @Operation(summary = "社交举报裁决",
            description = "result=1 成立 → 内容置举报下架并通知作者；result=2 不成立 → 内容不动")
    @PostMapping("/{reportId}/handle")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REVIEW_JUDGE)
    @OpLog(module = "social", type = "SOCIAL_REPORT_HANDLE", bizId = "#reportId",
            desc = "社交举报裁决", saveDiff = true)
    public Result<Void> handle(@PathVariable("reportId") Long reportId,
                               @Valid @RequestBody ReportHandleReq req) {
        socialReportAdminService.handle(reportId, req);
        return Result.ok();
    }
}

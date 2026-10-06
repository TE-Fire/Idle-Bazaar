package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.Result;
import com.campus.core.dto.req.AppealJudgeReq;
import com.campus.core.dto.req.ItemOfflineReq;
import com.campus.core.dto.req.ReportHandleReq;
import com.campus.core.dto.req.ReviewRejectReq;
import com.campus.core.dto.resp.ReviewQueueVO;
import com.campus.core.oplog.OpLog;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.ReviewAdminService;
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
 * 审核队列（管理端 A5 `/review`，流程见 notes/09）。
 *
 * <p><b>权限</b>：队列查询挂 `review.view`（17 个权限点里补的只读点，四个角色都有——
 * F-A1 矩阵说只读角色「能看全部数据」，此前只能借 `review.judge` 做门禁，只读角色连页面都进不去）。
 * 三个写接口各挂各的：`review.judge`（通过 / 驳回 / 举报裁决）、`review.forceoff`（强制下架）、
 * `review.appeal`（申诉复审）。运营与只读只有 `review.view`，进得来、动不了。
 *
 * <p><b>没有单独的详情接口</b>：队列不分页，整条任务的字段（含商品图与凭证）一次给全，
 * 前端 Drawer 直接用列表里那一份就行。加一个详情接口只会多一次往返，
 * 而单条处置 ≤30 秒是 SLA 的硬指标（business/03 F-A2）。
 *
 * <p><b>强制下架与商品页共用同一个 Service 方法</b>：两处处置结果必须一致，
 * 这里只多一道「单据仍是待审核」的前置校验（重复处置 → 3001）。
 *
 * <p>写接口的 `@OpLog` 已补齐，五个写接口各一条：`APPROVE` / `REJECT` /
 * `FORCE_OFF` / `REPORT_HANDLE` / `APPEAL_JUDGE`，`module = review`。
 * 注意它能记到的只是「过了鉴权之后的成败」——权限拒绝由 `SaInterceptor` 在
 * 进入方法前拦下（1002），早于切面，所以「谁试图越权」要看访问日志，不在本表。
 */
@Tag(name = "管理端 · 审核队列")
@RestController
@RequestMapping("/admin/review")
@RequiredArgsConstructor
public class ReviewAdminController {

    private final ReviewAdminService reviewService;

    @Operation(summary = "审核队列",
            description = "商品审核 / 举报 / 申诉三来源合并，不分页。"
                    + "排序：申诉 > 举报 > 商品审核，组内按权重降序（权重含实时等待补偿）。"
                    + "kind 可选过滤：item / report / appeal。同时返回 SLA 概览与阈值。")
    @GetMapping("/queue")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REVIEW_VIEW)
    public Result<ReviewQueueVO> queue(@RequestParam(value = "kind", required = false) String kind) {
        return Result.ok(reviewService.queue(kind));
    }

    @Operation(summary = "商品审核通过",
            description = "商品「待审核 → 在售」并写 published_at；审核单据转「已通过」，记处置人与首审时间。")
    @PostMapping("/item/{auditId}/pass")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REVIEW_JUDGE)
    @OpLog(module = "review", type = "APPROVE", bizId = "#auditId", desc = "审核通过")
    public Result<Void> pass(@PathVariable("auditId") Long auditId) {
        reviewService.pass(auditId);
        return Result.ok();
    }

    @Operation(summary = "商品审核驳回",
            description = "商品「待审核 → 审核未过」并记原因（卖家据此修改后重提）；审核单据转「已驳回」。")
    @PostMapping("/item/{auditId}/reject")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REVIEW_JUDGE)
    @OpLog(module = "review", type = "REJECT", bizId = "#auditId", desc = "审核驳回", saveDiff = true)
    public Result<Void> reject(@PathVariable("auditId") Long auditId,
                               @Valid @RequestBody ReviewRejectReq req) {
        reviewService.reject(auditId, req);
        return Result.ok();
    }

    @Operation(summary = "商品强制下架（审核入口）",
            description = "与商品页下架是同一个动作：商品转「已强制下架」，审核单据转「已强制下架」。"
                    + "终态，要恢复只能走申诉。")
    @PostMapping("/item/{auditId}/force-offline")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REVIEW_FORCE_OFF)
    @OpLog(module = "review", type = "FORCE_OFF", bizId = "#auditId", desc = "强制下架（审核入口）", saveDiff = true)
    public Result<Void> forceOffline(@PathVariable("auditId") Long auditId,
                                     @Valid @RequestBody ItemOfflineReq req) {
        reviewService.forceOffline(auditId, req);
        return Result.ok();
    }

    @Operation(summary = "举报裁决",
            description = "result=1 成立 → 商品强制下架；result=2 不成立 → 商品不动。"
                    + "成立时 reason（处置依据）必填。不做独立的「受理」步骤，裁决一步到位。")
    @PostMapping("/report/{reportId}/handle")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REVIEW_JUDGE)
    @OpLog(module = "review", type = "REPORT_HANDLE", bizId = "#reportId", desc = "举报裁决", saveDiff = true)
    public Result<Void> handleReport(@PathVariable("reportId") Long reportId,
                                     @Valid @RequestBody ReportHandleReq req) {
        reviewService.handleReport(reportId, req);
        return Result.ok();
    }

    @Operation(summary = "申诉复审",
            description = "result=2 维持（商品不动）/ 3 撤销（商品按原处置反向恢复）。"
                    + "复审人不得是原处置人（F-A4），命中返回 3002。")
    @PostMapping("/appeal/{appealId}/judge")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REVIEW_APPEAL)
    @OpLog(module = "review", type = "APPEAL_JUDGE", bizId = "#appealId", desc = "申诉复审", saveDiff = true)
    public Result<Void> judgeAppeal(@PathVariable("appealId") Long appealId,
                                    @Valid @RequestBody AppealJudgeReq req) {
        reviewService.judgeAppeal(appealId, req);
        return Result.ok();
    }
}

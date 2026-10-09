package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.AppealCreateReq;
import com.campus.core.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端申诉。
 *
 * <p>`bizType` 决定申诉对象：1 商品审核处置、2 举报处置、3 订单纠纷。
 * 第三种此前在队列里被丢弃，订单模块落地后才真正接进审核队列（`notes/13` 第五节），
 * 裁决结果由管理端 `ReviewAdminService#judgeAppeal` 落到订单终态。
 */
@Tag(name = "学生端 · 申诉")
@RestController
@RequestMapping("/api/appeal")
@RequiredArgsConstructor
public class AppealController {

    private final ReportService reportService;

    @Operation(summary = "提交申诉", description = "bizType：1 商品审核 2 举报 3 订单纠纷；只有被处置方本人可提交")
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody AppealCreateReq req) {
        return Result.ok(reportService.appeal(UserContext.getUserId(), req));
    }
}

package com.campus.user.controller;

import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.req.ReportCreateReq;
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
 * 学生端举报。
 *
 * <p>本接口只写 `report.status = 1` 一行，业务判断全在 {@link ReportService#report} 里
 * （目标存在性、不许举报自己、同一人的未处理单据去重）。它体量小，却是审核队列第一次拥有
 * 真实数据来源——此前队列里只有演示数据（`notes/13` 第七节）。
 */
@Tag(name = "学生端 · 举报")
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "提交举报", description = "targetType：1 商品 2 用户；凭证 ≤3 张")
    @PostMapping
    public Result<Long> submit(@Valid @RequestBody ReportCreateReq req) {
        return Result.ok(reportService.report(UserContext.getUserId(), req));
    }
}

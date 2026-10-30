package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.Result;
import com.campus.core.dto.resp.DashboardVO;
import com.campus.core.dto.resp.ReportVO;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计接口：A2 看板 + A8 报表（notes/25 是口径来源）。
 *
 * <p>**不为统计单开错误码段**：只有两类失败——没权限（权限链路统一拦 1002）、
 * 参数非法（`days` 不是 7/30、`metric` 不是三个枚举值，一律 1000）。
 *
 * <p>全部是 GET、只读、无副作用，因此不挂 `@OpLog`——操作日志记的是写动作，
 * 谁看了报表不属于业务事实。
 */
@Tag(name = "管理端 · 统计（看板与报表）")
@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @Operation(summary = "看板六块一次返回", description = "days=7|30，只影响趋势区")
    @GetMapping("/dashboard")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.DASHBOARD_VIEW)
    public Result<DashboardVO> dashboard(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.dashboard(days));
    }

    @Operation(summary = "学期时间轴", description = "weeks=4..26，按自然周（周一为界）聚合，缺数据补 0")
    @GetMapping("/report/timeline")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REPORT_VIEW)
    public Result<ReportVO.Timeline> timeline(@RequestParam(defaultValue = "18") int weeks) {
        return Result.ok(statsService.timeline(weeks));
    }

    @Operation(summary = "学院 × 品类矩阵",
            description = "metric=sold（买家学院）|published（卖家学院）|rate（成交量÷发布量）")
    @GetMapping("/report/matrix")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REPORT_VIEW)
    public Result<ReportVO.Matrix> matrix(@RequestParam(defaultValue = "sold") String metric) {
        return Result.ok(statsService.matrix(metric));
    }

    @Operation(summary = "面交所热力", description = "面交点 × 五档时段；未约面交时间的单不计")
    @GetMapping("/report/meet-point")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REPORT_VIEW)
    public Result<ReportVO.MeetHeat> meetPoint() {
        return Result.ok(statsService.meetPoint());
    }

    @Operation(summary = "分审核员 SLA 明细", description = "含合计行；P95 取排序后第 95 百分位，不插值")
    @GetMapping("/report/sla")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.REPORT_VIEW)
    public Result<ReportVO.SlaTable> sla() {
        return Result.ok(statsService.slaTable());
    }
}

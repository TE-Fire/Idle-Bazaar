package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.dto.req.OperationLogReq;
import com.campus.core.dto.resp.OperationLogVO;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作日志（管理端 A9 `/settings/logs`，业务口径见 business/03 F-A11）。
 *
 * <p><b>权限</b>：`log.view`。超级管理员与运营都有、审核员没有（business/03 F-A1）——
 * 审核员能处置商品但翻不到全局留痕，这是矩阵刻意的划分，不是漏给。
 *
 * <p><b>只有一个接口，且没有 `@OpLog`</b>：本表只记「有业务语义的写动作」，
 * 读日志这件事由访问日志负责留痕（`AccessLogFilter` 打文件）。给查询接口挂 `@OpLog`
 * 会形成「查日志本身又产生日志」的自指，翻一页就多一条记录，很快把审计表冲成噪音。
 *
 * <p><b>没有删除接口</b>：这是审计表，只追加。F-A11 明确「A9 页面只读」。
 */
@Tag(name = "管理端 · 操作日志")
@RestController
@RequestMapping("/admin/log")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService operationLogService;

    @Operation(summary = "操作日志列表",
            description = "分页，按操作时间倒序。module 传英文码（item / review / content / system）；"
                    + "keyword 模糊匹配操作者姓名 / 操作类型 / 描述；result 传 1 筛成功、0 筛失败；"
                    + "startTime / endTime 传 yyyy-MM-dd 日期（含当天）。")
    @GetMapping("/list")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.LOG_VIEW)
    public Result<PageResult<OperationLogVO>> list(OperationLogReq req) {
        return Result.ok(operationLogService.list(req));
    }
}

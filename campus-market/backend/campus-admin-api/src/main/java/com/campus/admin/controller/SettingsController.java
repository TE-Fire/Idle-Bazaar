package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.Result;
import com.campus.core.dto.req.SlaConfigUpdateReq;
import com.campus.core.dto.resp.SlaConfigVO;
import com.campus.core.oplog.OpLog;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统设置 · SLA 阈值（`sys_config`，口径见 notes/25 §四）。
 *
 * <p>阈值落库而不是前端常量：设置页改完刷新就丢过一次，「可调」就变成了一句空话。
 * 读写下都用 `settings.edit`——能看阈值的人就是能改的人，没有只读场景。
 */
@Tag(name = "管理端 · 系统设置")
@RestController
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SysConfigService sysConfigService;

    @Operation(summary = "读 SLA 六个阈值")
    @GetMapping("/sla")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
    public Result<SlaConfigVO> sla() {
        return Result.ok(sysConfigService.sla());
    }

    @Operation(summary = "写 SLA 六个阈值", description = "全量六键，改完立刻影响看板与报表的达标率")
    @PutMapping("/sla")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
    @OpLog(module = "system", type = "SLA_UPDATE", desc = "调整 SLA 阈值", saveDiff = true)
    public Result<Void> updateSla(@Valid @RequestBody SlaConfigUpdateReq req) {
        sysConfigService.updateSla(req);
        return Result.ok();
    }
}

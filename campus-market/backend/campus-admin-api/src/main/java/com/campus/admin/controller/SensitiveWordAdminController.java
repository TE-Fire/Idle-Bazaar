package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.dto.req.SensitiveWordSaveReq;
import com.campus.core.dto.req.SensitiveWordStatusReq;
import com.campus.core.dto.resp.SensitiveWordVO;
import com.campus.core.oplog.OpLog;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.SensitiveWordAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 敏感词库维护（管理端 A9 系统设置，notes/22 §五 的写入口）。
 *
 * <p>词库同时作用于发帖、动态评论、商品评论与商品描述——**一个词改全端生效**，
 * 所以整组接口只挂 `settings.edit`（与字典维护同样的收口理由：一改三端同步变）。
 *
 * <p>改完由 Service 调 `SensitiveWordService#refresh()` 自增 `cm:sensitive:ver`，
 * 各实例 3 秒内惰性重载。管理端不碰缓存，缓存的一致性由版本戳统一负责。
 *
 * <p>全部写接口挂 `@OpLog`（`module = system`）：词库变更直接改变「什么能发出去」，
 * 事后必须能回答「这条高危词是谁、什么时候加的」。
 */
@Tag(name = "管理端 · 敏感词库")
@RestController
@RequestMapping("/admin/sensitive")
@RequiredArgsConstructor
public class SensitiveWordAdminController {

    private final SensitiveWordAdminService sensitiveWordAdminService;

    @Operation(summary = "敏感词列表",
            description = "level：1 高危 / 2 低危；status：1 启用 0 停用；均不传则不过滤")
    @GetMapping("/list")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
    public Result<PageResult<SensitiveWordVO>> list(
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize) {
        return Result.ok(sensitiveWordAdminService.list(level, status, pageNum, pageSize));
    }

    @Operation(summary = "新增敏感词", description = "同词重复报 4026（停用词也算重复）；level 缺省按 2 低危")
    @PostMapping
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
    @OpLog(module = "system", type = "SENSITIVE_WORD_CREATE", desc = "新增敏感词", saveDiff = true)
    public Result<Void> create(@Valid @RequestBody SensitiveWordSaveReq req) {
        sensitiveWordAdminService.create(req);
        return Result.ok();
    }

    @Operation(summary = "编辑敏感词", description = "改词面或级别；同词重复报 4026")
    @PutMapping("/{id}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
    @OpLog(module = "system", type = "SENSITIVE_WORD_UPDATE", bizId = "#id",
            desc = "编辑敏感词", saveDiff = true)
    public Result<Void> update(@PathVariable("id") Long id,
                               @Valid @RequestBody SensitiveWordSaveReq req) {
        sensitiveWordAdminService.update(id, req);
        return Result.ok();
    }

    @Operation(summary = "启停敏感词", description = "停用后立刻从内存快照摘掉，不再参与过滤")
    @PostMapping("/{id}/status")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
    @OpLog(module = "system", type = "SENSITIVE_WORD_STATUS", bizId = "#id",
            desc = "启停敏感词", saveDiff = true)
    public Result<Void> updateStatus(@PathVariable("id") Long id,
                                     @Valid @RequestBody SensitiveWordStatusReq req) {
        sensitiveWordAdminService.updateStatus(id, req.getEnabled());
        return Result.ok();
    }

    @Operation(summary = "删除敏感词", description = "软删；同词可再次新增")
    @DeleteMapping("/{id}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.SETTINGS_EDIT)
    @OpLog(module = "system", type = "SENSITIVE_WORD_DELETE", bizId = "#id", desc = "删除敏感词")
    public Result<Void> delete(@PathVariable("id") Long id) {
        sensitiveWordAdminService.delete(id);
        return Result.ok();
    }
}

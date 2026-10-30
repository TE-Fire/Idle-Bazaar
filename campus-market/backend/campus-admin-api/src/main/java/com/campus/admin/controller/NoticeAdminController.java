package com.campus.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.campus.common.constant.PermCodes;
import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.dto.req.NoticePublishReq;
import com.campus.core.dto.resp.AdminNoticeVO;
import com.campus.core.oplog.OpLog;
import com.campus.core.satoken.StpAdminUtil;
import com.campus.core.service.NoticeAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台公告（管理端 A11，notes/22 S12 / business/03 F-A12）。
 *
 * <p>权限复用 `content.edit`：发公告与改字典同属「运营对外说一句话」，不新增权限点。
 *
 * <p>本期收窄三处：不定时发布（发布即送达）、不写富文本（标题 + 纯文本）、不定向人群（全员或按校区）。
 */
@Tag(name = "管理端 · 平台公告")
@RestController
@RequestMapping("/admin/notice")
@RequiredArgsConstructor
public class NoticeAdminController {

    private final NoticeAdminService noticeAdminService;

    @Operation(summary = "公告列表", description = "按批次聚合，一条公告一行；撤回后仍保留并标 revoked")
    @GetMapping("/list")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    public Result<PageResult<AdminNoticeVO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(noticeAdminService.list(pageNum, pageSize));
    }

    @Operation(summary = "发布公告",
            description = "title 可空、content 必填 ≤2000、campusId 空=全员；返回 batchId")
    @PostMapping
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    @OpLog(module = "content", type = "NOTICE_PUBLISH", desc = "发布平台公告", saveDiff = true)
    public Result<Long> publish(@Valid @RequestBody NoticePublishReq req) {
        return Result.ok(noticeAdminService.publish(req));
    }

    @Operation(summary = "撤回公告", description = "按批次软删收件行；学生端该公告消失，管理端列表仍保留")
    @DeleteMapping("/{batchId}")
    @SaCheckPermission(type = StpAdminUtil.TYPE, value = PermCodes.CONTENT_EDIT)
    @OpLog(module = "content", type = "NOTICE_REVOKE", bizId = "#batchId", desc = "撤回平台公告")
    public Result<Integer> revoke(@PathVariable("batchId") Long batchId) {
        return Result.ok(noticeAdminService.revoke(batchId));
    }
}

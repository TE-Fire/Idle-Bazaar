package com.campus.user.controller;

import com.campus.common.result.PageResult;
import com.campus.common.result.Result;
import com.campus.core.context.UserContext;
import com.campus.core.dto.resp.NoticeVO;
import com.campus.core.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端系统通知（notes/22 批次 3）：复用 `msg_message` 的 `zone=2` 分区。
 *
 * <p>只有平台发出的通知（`sender_id=0`）走这里，私信仍在 chat 通道，两者不混在一个未读数里。
 */
@Tag(name = "学生端 · 系统通知")
@RestController
@RequestMapping("/api/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "通知列表", description = "时间倒序分页")
    @GetMapping("/list")
    public Result<PageResult<NoticeVO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(noticeService.list(UserContext.getUserId(), pageNum, pageSize));
    }

    @Operation(summary = "未读条数")
    @GetMapping("/unread")
    public Result<Long> unread() {
        return Result.ok(noticeService.unread(UserContext.getUserId()));
    }

    @Operation(summary = "全部标记已读")
    @PostMapping("/read-all")
    public Result<Void> readAll() {
        noticeService.markAllRead(UserContext.getUserId());
        return Result.ok();
    }
}

package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知一行（`msg_message` 的 `zone=2 / sender_id=0` 子集）。
 *
 * <p>`type` 4 系统处置 / 5 互动（被赞·被评·被关注）/ 6 平台公告，前端按它分色与选图标。
 * `title` 只有公告有（存在 `extra.title`），处置与互动通知为空，前端据此决定是否加粗标题行。
 */
@Data
public class NoticeVO {

    private Long id;

    /** 4 系统 / 5 互动 / 6 平台公告 */
    private Integer type;

    /** 公告标题；非公告为 null */
    private String title;

    private String content;

    /** 0 未读 1 已读 */
    private Integer readFlag;

    private LocalDateTime createTime;
}

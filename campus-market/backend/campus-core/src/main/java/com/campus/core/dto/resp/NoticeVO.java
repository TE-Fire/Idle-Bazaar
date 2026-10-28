package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统通知一行（`msg_message` 的 `zone=2 / sender_id=0` 子集，notes/22 批次 3）。
 */
@Data
public class NoticeVO {

    private Long id;

    private String content;

    /** 0 未读 1 已读 */
    private Integer readFlag;

    private LocalDateTime createTime;
}

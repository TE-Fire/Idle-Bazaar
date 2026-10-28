package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知一行（`msg_message` 的 `zone=2 / sender_id=0` 子集）。
 *
 * <p>`type` 4 系统处置 / 5 互动（被赞·被评·被关注），前端按它分色与选图标。
 */
@Data
public class NoticeVO {

    private Long id;

    /** 4 系统 / 5 互动 */
    private Integer type;

    private String content;

    /** 0 未读 1 已读 */
    private Integer readFlag;

    private LocalDateTime createTime;
}

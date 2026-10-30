package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告一行（管理端 A11），一条公告 = 一个 `batch_id`。
 *
 * <p>`receiverCount` 是**发出时**写下的行数，`readCount` 是其中已读的行数；
 * 撤回后 `revoked=true` 且 `readCount` 归零（收件行都软删了），两个数各自诚实。
 */
@Data
public class AdminNoticeVO {

    private Long batchId;

    private String title;

    private String content;

    /** 空 = 全员 */
    private Long campusId;

    /** 写下的收件行数 */
    private Integer receiverCount;

    /** 仍存活（未撤回）的收件行数，0 即已撤回 */
    private Integer aliveCount;

    private Integer readCount;

    /** 已撤回 = 没有存活行 */
    private Boolean revoked;

    private LocalDateTime createTime;
}

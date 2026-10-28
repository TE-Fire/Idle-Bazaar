package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评论一行（notes/22 S7）。`replyToNick` 为展示名（拼「回复 @某某」）。
 */
@Data
public class ItemCommentVO {

    private Long id;

    private Long itemId;

    private Long userId;

    private UserPubVO author;

    private Long replyTo;

    private String replyToNick;

    private String content;

    private LocalDateTime createTime;
}

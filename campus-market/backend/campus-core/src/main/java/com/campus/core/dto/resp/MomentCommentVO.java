package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论一行（notes/22 S6）。`replyTo` 存目标用户 id，`replyToNick` 为展示名（拼「回复 @某某」）。
 */
@Data
public class MomentCommentVO {

    private Long id;

    private Long momentId;

    private Long userId;

    private UserPubVO author;

    private Long replyTo;

    private String replyToNick;

    private String content;

    private LocalDateTime createTime;
}

package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商品评论请求（notes/22 S7）。
 *
 * <p>`replyTo` 存目标用户 id，可空；长度与敏感词在 Service 内校验。
 */
@Data
public class ItemCommentReq {

    @NotBlank(message = "评论内容不能为空")
    private String content;

    /** 回复目标用户 id，可空 */
    private Long replyTo;
}

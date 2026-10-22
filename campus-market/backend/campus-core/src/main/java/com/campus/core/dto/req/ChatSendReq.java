package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送询单留言。会话按「商品 + 双方」定位，故只需要对方 id 与商品 id——
 * 不需要传 sessionId（前端也拿不到，会话由服务端 upsert）。
 */
@Data
public class ChatSendReq {

    @NotNull
    private Long itemId;

    @NotNull
    private Long toUserId;

    /** 文本留言：1–500 字。图片消息与面交卡片本期不做 */
    @NotBlank
    @Size(max = 500)
    private String content;
}

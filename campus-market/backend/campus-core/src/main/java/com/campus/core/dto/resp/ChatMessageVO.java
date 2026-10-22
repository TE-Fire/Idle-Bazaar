package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息。`mine` 由服务端按发送人与当前登录用户比较给出，
 * 前端不自己判断左右气泡——判断逻辑放一端，双端不会再各写错一遍。
 */
@Data
public class ChatMessageVO {

    private Long id;

    private Long senderId;

    private String content;

    private LocalDateTime createdAt;

    private boolean mine;
}

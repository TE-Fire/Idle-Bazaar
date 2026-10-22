package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 消息（`msg_message`，原 IM 三分区设计表，同步询单模式只用其子集）。
 *
 * <p>同步询单口径：zone 恒 1（私信）、type 恒 1（文本）——系统/交易通知与图片消息、
 * 面交卡片本期不做（business/01 决策表 2026-10-22）。二期升级实时 IM 时表结构不用动。
 */
@Data
@Accessors(chain = true)
@TableName("msg_message")
public class MsgMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分区：1 私信（同步询单恒 1） */
    private Integer zone;

    private Long sessionId;

    /** 发送者；系统通知为 0，本期无系统消息 */
    private Long senderId;

    private Long receiverId;

    /** 类型：1 文本（本期恒 1） */
    private Integer type;

    private String content;

    /** 0 未读 1 已读；拉取即标记已读 */
    private Integer readFlag;

    private LocalDateTime readTime;

    private LocalDateTime createTime;

    private Long deleted;
}

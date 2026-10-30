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
 * <p>四个分区/类型在用：私信 `zone=1 type=1`、系统处置 `zone=2 type=4`、
 * 互动 `zone=2 type=5`、平台公告 `zone=2 type=6`（公告多写一个 `batchId` 供按批撤回）。
 */
@Data
@Accessors(chain = true)
@TableName("msg_message")
public class MsgMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 分区：1 私信 / 2 系统通知 */
    private Integer zone;

    private Long sessionId;

    /** 发送者；系统与公告为 0 */
    private Long senderId;

    private Long receiverId;

    /** 类型：1 文本 / 4 系统处置 / 5 互动 / 6 平台公告 */
    private Integer type;

    private String content;

    /** 结构化载荷（JSON 串）：面交卡片与公告标题存这里 */
    private String extra;

    /** 公告批次：同一条公告的所有收件行同值，撤回按它批量软删 */
    private Long batchId;

    /** 0 未读 1 已读；系统通知「全部已读」批量置 1 */
    private Integer readFlag;

    private LocalDateTime readTime;

    private LocalDateTime createTime;

    private Long deleted;
}

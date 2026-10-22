package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 聊天会话（`chat_session`）。按「商品 + 双方」唯一（uk_pair），无商品会话 item_id 为 0。
 *
 * <p>user_a / user_b 按 userId 大小排序存放：任一方查询都是同一个 (a,b) 组合，
 * 不会出现「A 找 B」与「B 找 A」两行。未读数以 `msg_message.read_flag` 实时统计为准，
 * 本表 a_unread / b_unread 是原 IM 设计的库内兜底列，同步询单模式不维护。
 */
@Data
@Accessors(chain = true)
@TableName("chat_session")
public class ChatSession {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long itemId;

    private Long userA;

    private Long userB;

    private Long lastMsgId;

    private LocalDateTime lastMsgAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long deleted;
}

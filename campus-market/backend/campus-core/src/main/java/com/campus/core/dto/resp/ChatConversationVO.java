package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表项。按「商品 + 对方」一行：买家看到的是「在哪件商品下和谁聊过」，
 * 卖家看到的是「谁在哪件商品下问过我」——双方同一行数据，不用两套逻辑。
 */
@Data
public class ChatConversationVO {

    private Long itemId;

    private String itemTitle;

    private String itemMainImage;

    private Long peerUserId;

    private String peerNick;

    private String lastContent;

    private LocalDateTime lastAt;

    /** 对方发来且未读的条数；拉取消息后清零 */
    private int unread;
}

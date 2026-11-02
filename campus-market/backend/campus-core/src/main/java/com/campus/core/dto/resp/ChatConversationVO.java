package com.campus.core.dto.resp;

import com.campus.core.enums.ItemStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
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

    /** 商品价格；会话窗顶部商品卡片展示，面交谈价有参照 */
    private BigDecimal price;

    /** 商品状态（枚举名）；已售出 / 已下架等会话卡片上置灰提示 */
    private ItemStatusEnum status;

    private Long peerUserId;

    private String peerNick;

    /** 对方头像；会话列表与消息气泡都靠它，为空时前端回退昵称首字 */
    private String peerAvatar;

    private String lastContent;

    private LocalDateTime lastAt;

    /** 对方发来且未读的条数；拉取消息后清零 */
    private int unread;
}

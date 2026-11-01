package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 议价单（`item_offer`）：买家对可议价商品的出价，卖家同意后买家按该价付款。
 *
 * <p><b>价格隔离是整个设计的前提</b>：议价价只存在这张表和被它成交的那笔订单上，
 * 从不写回 `item.price`。所以议价成功后买家没付款，其他买家看到的仍是标价，
 * 商品均价缓存也不需要失效——没有第二个读取方，就没有不一致的可能。
 *
 * <p>`origin_price` 是出价那一刻的标价快照：卖家事后改价，已发出的议价仍按原价基准判定，
 * 否则「出价 80、卖家改成 100、再改成 60」这类操作会让同一张单据的合法性反复变化。
 *
 * <p>`alive` 是数据库生成列（仅 1/2 为 1，其余 NULL），不映射进实体：
 * 它只服务于唯一索引 {@code uk_alive}，保证同一买家对同一商品同时只有一条进行中议价，
 * 由数据库兜住连点，而不是靠先查后插。
 */
@Data
@Accessors(chain = true)
@TableName("item_offer")
public class ItemOffer {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long itemId;

    private Long buyerId;

    /** 商品卖家，冗余存一份：卖家待办列表按它建索引，不必回查商品 */
    private Long sellerId;

    /** 出价时的标价快照 */
    private BigDecimal originPrice;

    private BigDecimal offerPrice;

    /** 见 {@link com.campus.core.enums.OfferStatusEnum} */
    private Integer status;

    /** 付款截止：卖家同意时写 now+10 分钟；仅 status=2 有意义 */
    private LocalDateTime expireTime;

    private LocalDateTime handleTime;

    private String rejectReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

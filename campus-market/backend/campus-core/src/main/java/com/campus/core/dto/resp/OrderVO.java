package com.campus.core.dto.resp;

import com.campus.core.enums.OrderStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单视图（列表与详情共用，`notes/13` 第七节的订单接口）。
 *
 * <p><b>不暴露 `snapshot`</b>：它是纠纷取证用的原始 JSON，只在管理端复盘时出现；
 * 给学生看的话，里面包含着下单那一刻的完整商品字段，既冗余也容易让人误以为这是当前状态。
 *
 * <p><b>暴露 `verifyCode` 而不隐藏</b>：买家与卖家都需要它——一单一码、任一方扫码即核销，
 * 隐藏了就等于把核销能力收回到后台，面交现场就验不了。
 *
 * <p>`itemTitle` / `itemPrice` 等五个快照字段照常输出：列表页要展示「当时买的什么东西」，
 * 这些值不随卖家后来改商品而变化，正是快照的意义。
 */
@Data
public class OrderVO {

    private Long id;

    private String orderNo;

    private Long itemId;

    private Long buyerId;

    private Long sellerId;

    /** 买家昵称（详情页展示交易对方用） */
    private String buyerNick;

    /** 买家头像（绝对或相对 URL，空时前端按 buyerId 派生） */
    private String buyerAvatar;

    /** 卖家昵称 */
    private String sellerNick;

    /** 卖家头像 */
    private String sellerAvatar;

    private BigDecimal amount;

    /** 输出枚举名，前端不必维护 1–8 的映射 */
    private OrderStatusEnum status;

    private Long meetPointId;

    /** 面交点名称（约定面交地点，如「一食堂门口」） */
    private String meetPointName;

    /** 面交点所属校区名（如「主校区」），从 dict_campus 冗余取出省二次查询 */
    private String meetPointCampus;

    private LocalDateTime meetTime;

    private String verifyCode;

    private LocalDateTime verifyTime;

    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    /** 取消方 1 买家 2 卖家 3 系统超时；违约判定要看它 */
    private Integer cancelBy;

    private String cancelReason;

    private String itemTitle;

    private BigDecimal itemPrice;

    private String itemGrade;

    private String itemMainImage;

    private LocalDateTime createTime;
}

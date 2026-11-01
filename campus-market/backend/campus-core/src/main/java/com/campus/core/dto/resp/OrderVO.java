package com.campus.core.dto.resp;

import com.campus.core.enums.DeliveryTypeEnum;
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
 * <p><b>只在卖家侧携带 `verifyCode`</b>：防单方核销——码由卖家持有、只能由买家录入核销，
 * 因此后端只对卖家回填该字段，买家侧恒为空（前端据此隐藏码、只亮输入框）。
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

    /** 议价单 id：非空表示这笔按议价价成交，前端可据此显示「议价成交」 */
    private Long offerId;

    /** 输出枚举名，前端不必维护 1–11 的映射 */
    private OrderStatusEnum status;

    /** 收货方式（枚举名），决定履约动作与地址交互 */
    private DeliveryTypeEnum deliveryType;

    /** 地址 id（送货上门=买家收货地址；自提=卖家提货地址；面交为空） */
    private Long addressId;

    /** 地址快照·联系人 */
    private String addressContact;

    /** 地址快照·联系电话 */
    private String addressPhone;

    /** 地址快照·完整地址（省+市+区+详细） */
    private String addressDetail;

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

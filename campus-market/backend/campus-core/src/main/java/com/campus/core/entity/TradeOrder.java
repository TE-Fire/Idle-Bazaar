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
 * 订单（`trade_order`，状态机与口径见 notes/13）。
 *
 * <p><b>无预定机制</b>：下单只插入一条待付款订单，商品保持在售不动；
 * 所有权判定的唯一时刻是付款成功的那一刻（`tech/01` §9.1 的两级校验）。
 * 因此本表没有任何反映「锁定库存」的字段——那是被明确否掉的设计。
 *
 * <p><b>`amount` 一律按标价</b>：议价不是独立业务，只作为商品的展示标签，
 * 口头议价发生在聊天里（`business/01` 第七节已决策）。订单不记录议价过程也不接受自定义金额。
 *
 * <p><b>五个 `item_*` 快照字段与 `snapshot` 不可回写</b>：它们的存在是为了防止卖家事后改价改描述，
 * 使纠纷取证有可信数据源。卖家后续修改商品时，历史订单的快照必须保持下单时的原样。
 *
 * <p>`version` 服务于订单自身的乐观锁；商品侧的占位并发另有 `item.version` 承担，两者互相独立。
 *
 * <p><b>收货地址同样在下单时快照进 `address_contact/phone/detail`</b>：与商品快照同理，防止卖家改提货点、
 * 买家改收货地址后双方对「货去哪了」各执一词。面交订单无地址快照，地址信息由 `meet_point_id` 承载。
 */
@Data
@Accessors(chain = true)
@TableName("trade_order")
public class TradeOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单号，对外展示与支付幂等键 */
    private String orderNo;

    private Long itemId;

    private Long buyerId;

    private Long sellerId;

    /** 成交金额，一律取自下单时的商品标价 */
    private BigDecimal amount;

    /** 见 {@link com.campus.core.enums.OrderStatusEnum} */
    private Integer status;

    /** 收货方式：1面交 2自提 3送货上门（见 DeliveryTypeEnum），下单时从商品抄入，之后不再随商品改动 */
    private Integer deliveryType;

    private Long meetPointId;

    /** 约定面交时间，下单时由双方约定 */
    private LocalDateTime meetTime;

    /** 地址引用：送货上门=买家收货地址；自提=卖家提货地址；面交为空 */
    private Long addressId;

    /** 地址快照·联系人（下单时固化，防止事后改地址引发纠纷取证困难） */
    private String addressContact;

    /** 地址快照·联系电话 */
    private String addressPhone;

    /** 地址快照·完整地址（省+市+区+详细） */
    private String addressDetail;

    /** 核销码，一单一张（`uk_verify_code` 唯一），任一方扫码即成立 */
    private String verifyCode;

    private LocalDateTime verifyTime;

    /** 扫码方 `app_user.id`，用于复盘而非授权 */
    private Long verifyBy;

    /** 付款截止时间，下单 + 30 分钟；超时扫描与延迟消息都以此为判据 */
    private LocalDateTime expireTime;

    private LocalDateTime payTime;

    /** 取消方，见 {@link com.campus.core.enums.CancelTypeEnum} */
    private Integer cancelBy;

    /** 取消原因，违约判定的依据 */
    private String cancelReason;

    private String itemTitle;

    private BigDecimal itemPrice;

    private String itemGrade;

    private String itemMainImage;

    /** 完整商品信息 JSON，纠纷取证用 */
    private String snapshot;

    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

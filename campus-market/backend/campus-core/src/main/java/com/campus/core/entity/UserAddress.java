package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户地址（`user_address`，收货 SHIP / 自提 PICKUP 两类同表）。
 *
 * <p><b>收货地址由买家维护</b>：送货上门订单下单时从中选取；
 * <b>自提地址由卖家维护</b>：发布自提商品时从中选取一个作为提货点。两类靠 `kind` 区分，互不可见。
 *
 * <p>地址被订单引用后在下单那一刻快照进 `trade_order`（见该实体注释），
 * 因此本表只是「用户的可选地址簿」，改它不影响历史订单——这是刻意的设计，避免改地址后纠纷取证失准。
 */
@Data
@Accessors(chain = true)
@TableName("user_address")
public class UserAddress {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属用户（买家维护收货地址，卖家维护自提地址） */
    private Long userId;

    /** 1 收货地址(SHIP) 2 自提地址(PICKUP)，见 AddressKindEnum */
    private Integer kind;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    private String province;

    private String city;

    private String district;

    /** 详细地址（门牌 / 楼栋 / 摊位号等） */
    private String detail;

    /** 是否默认地址（同类地址唯一默认） */
    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

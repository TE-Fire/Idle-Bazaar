package com.campus.core.dto.resp;

import com.campus.core.enums.OfferStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 议价单视图（「我的议价」列表与聊天卡片点开后的详情共用）。
 *
 * <p><b>`itemPrice` 是商品当前标价，不是议价价</b>：页面上两个数字要同时出现才讲得清
 * 「原价 500，你出 420」；只给议价价会让人以为商品降价了。
 *
 * <p>`expireTime` 只在已同意时有值，前端据此显示倒计时；其余状态一律为空，
 * 免得出现过期时间却又不允许付款的矛盾提示。
 */
@Data
public class OfferVO {

    private Long id;

    private Long itemId;

    private String itemTitle;

    private String itemMainImage;

    /** 商品当前标价（其他买家看到的价） */
    private BigDecimal itemPrice;

    /** 出价那一刻的标价快照 */
    private BigDecimal originPrice;

    private BigDecimal offerPrice;

    private OfferStatusEnum status;

    /** 付款截止，仅 status=AGREED 有值 */
    private LocalDateTime expireTime;

    private Long buyerId;

    private Long sellerId;

    /** 对方昵称：买家查看时是卖家，卖家查看时是买家 */
    private String peerNick;

    private String rejectReason;

    private LocalDateTime createTime;

    /** 当前登录用户是否卖方视角，决定前端展示「同意/拒绝」还是「去付款」 */
    private boolean asSeller;
}

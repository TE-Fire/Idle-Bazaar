package com.campus.core.dto.resp;

import com.campus.core.enums.OrderStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理端订单视图（列表与详情共用，`business/03` A6）。
 *
 * <p><b>与 {@link OrderVO} 的差别是「快照 vs 当前商品」成对出现</b>：管理端的核心诉求是纠纷取证，
 * 页面必须能看见「下单时是什么价、什么成色」，同时知道「卖家后来改成了什么」才判断得出有没有掉包。
 * 所以这里同时给 `itemPrice`（快照）与 `currentPrice`（现价），并由服务端算好 `snapshotDrifted`——
 * 漂移规则若交给前端，Web 与小程序迟早各写一套。
 *
 * <p><b>不输出 `verifyCode`</b>：核销码一单一码，管理端没有核销场景，
 * 输出等于凭空多一个能改变订单状态的凭据。
 *
 * <p>`disputeBy` 由 `applicantId` 与 `buyerId` 比对得出（1 买家 2 卖家）：申诉表只存了申请人 id，
 * 管理端要一眼看出「谁在主张权利」。
 */
@Data
public class AdminOrderVO {

    private Long id;

    private String orderNo;

    private Long itemId;

    /* ── 下单时的商品快照 ── */

    private String itemTitle;

    private BigDecimal itemPrice;

    private String itemGrade;

    private String itemMainImage;

    /** 快照描述，详情抽屉里展示；列表不显示 */
    private String itemDescription;

    /* ── 当前商品（卖家可能已改过） ── */

    private BigDecimal currentPrice;

    private String currentGrade;

    private String currentDescription;

    /** 价格 / 成色 / 描述任一与快照不同即为 true */
    private Boolean snapshotDrifted;

    private BigDecimal amount;

    private OrderStatusEnum status;

    private Long buyerId;

    private String buyerNick;

    private Long sellerId;

    private String sellerNick;

    private Long meetPointId;

    private String meetPointName;

    private LocalDateTime meetTime;

    private LocalDateTime payTime;

    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    /** 取消方 1 买家 2 卖家 3 系统；管理端强制退款也记在这里（3） */
    private Integer cancelBy;

    private String cancelReason;

    /* ── 纠纷（仅 biz_type=3 的申诉单，取该订单最新一条） ── */

    /** 待裁决的申诉单 id；前端裁决时要带它 */
    private Long appealId;

    private String disputeReason;

    /** 1 买家发起 2 卖家发起 */
    private Integer disputeBy;

    private LocalDateTime disputeAt;

    private String disputeEvidence;

    /** 已处理时的结论备注 */
    private String disputeNote;
}

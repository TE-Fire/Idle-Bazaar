package com.campus.core.dto.resp;

import com.campus.core.enums.ItemStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生端商品视图（列表与详情共用）。
 *
 * <p><b>不复用 `ItemListVO`</b>：那是管理端的形状，`reportCount` 与 `riskScore` 是处置侧的
 * 治理数据（被举报几次、机器判定的风险分），下发到 C 端等于把风控细节摊给普通用户。
 * 这里刻意只留交易所需字段。
 *
 * <p><b>`images` 只有详情有</b>：列表页用 `mainImage` 就够，把九张图挂在每一行上会让列表响应
 * 膨胀一个数量级。
 *
 * <p>价格字段按 `item` 原样命名（`originPrice`，不是管理端 VO 里的 `originalPrice`）——
 * 新的合同不必继承那个笔误。
 */
@Data
public class ApiItemVO {

    private Long id;

    private String title;

    private String mainImage;

    private Long categoryId;

    private String categoryName;

    private BigDecimal price;

    /** 卖家填的划线价，可空 */
    private BigDecimal originPrice;

    /** 成色 A/B/C/D */
    private String grade;

    private Long campusId;

    private String campusName;

    private Long meetPointId;

    private String meetPointName;

    private Long sellerId;

    private String sellerNick;

    private Integer sellerCredit;

    /** 1 可议价；议价在聊天里进行，不进订单 */
    private Integer negotiable;

    private Integer deliveryIncluded;

    private String description;

    /** 对外只会出现 ON_SALE：搜索列表与个人列表过滤条件不同，但都不会漏出待审核商品 */
    private ItemStatusEnum status;

    private LocalDateTime publishAt;

    private Integer viewCount;

    /** 详情专用，列表为 null */
    private List<ItemImageVO> images;
}

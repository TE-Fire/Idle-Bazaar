package com.campus.core.dto.resp;

import com.campus.core.enums.ItemStatusEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品列表一行（对齐前端 `AdminItem`，`packages/shared/src/admin.ts`）。
 *
 * <p>比 `AdminItem` 少一个 `tints`——那是骨架期没有真图时的占位渐变，接了真接口后由前端删掉。
 *
 * <p><b>两个「举报数」不要混</b>：`reportCount` 是 `report` 表的**实时**计数（运营要看「又被举报了」）；
 * 审核权重里用的是 `item_audit.report_count`，那是**进队快照**，不随后续举报变化。
 *
 * <p><b>可空字段注意</b>：全局 Jackson 配了 `non_null`，null 字段不出现在 JSON 里，
 * 前端拿到的是 `undefined` 而不是 `null`（判空必须 `!x`，见 tech/02 §6.1）。
 *
 * <p>`categoryAvgPrice` 为 0 表示「该分类没有已成交订单」，前端 `isPriceAbnormal` 里
 * `avg <= 0` 直接返回 false，不会误标红。
 */
@Data
public class ItemListVO {

    private Long id;

    private String title;

    /** 主图，列表页免查图表；演示数据是外链，加载失败时前端回退成占位色块 */
    private String mainImage;

    private Long categoryId;

    /** 二级分类名，由字典回填 */
    private String categoryName;

    private BigDecimal price;

    /** 卖家填的划线价，可空 */
    private BigDecimal originalPrice;

    /** 成色 A/B/C/D */
    private String condition;

    private Long campusId;

    private String campusName;

    private Long meetPointId;

    /** 面交点名称，可空 */
    private String meetPointName;

    private Long sellerId;

    private String sellerNick;

    private Integer sellerCredit;

    /** 输出枚举名（如 ON_SALE），与前端 `AdminItem.status` 同形 */
    private ItemStatusEnum status;

    /** 发布时间 */
    private LocalDateTime publishAt;

    /** 审核通过时间；草稿与待审核为 null */
    private LocalDateTime auditAt;

    private Integer viewCount;

    /** 收藏数（前端「想要」列） */
    private Integer wantCount;

    private Integer reportCount;

    /** 图片风险分 0–100，取最新一条审核记录；无记录为 0 */
    private Integer riskScore;

    /** 同类目已成交均价；无成交为 0 */
    private BigDecimal categoryAvgPrice;

    private String description;
}

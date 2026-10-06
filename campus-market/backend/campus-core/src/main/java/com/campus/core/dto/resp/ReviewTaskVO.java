package com.campus.core.dto.resp;

import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.enums.QueueKindEnum;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 审核队列里的一行（对齐前端 `QueueTask`）。
 *
 * <p><b>三种来源压成一个形状</b>（notes/09 问题 3）：申诉 / 举报 / 商品审核的字段差异很大，
 * 但共用一套「权重排序 + 连续审核」交互，压成一种类型前端才不用写三套渲染逻辑。
 * 差异字段（`reason` / `evidence` / `handledBy`…）允许为 null——
 * 全局 Jackson 配了 `non_null`，null 字段根本不会出现在 JSON 里。
 *
 * <p><b>`id` 跨来源不唯一</b>：`item_audit` / `report` / `appeal` 是三张表的主键，
 * 可能出现相同的数字。前端做列表 key 要用 `kind + id`，处置时也要带 `kind`。
 *
 * <p><b>两个「举报数」</b>：`reportCount` 是 `report` 表的实时计数（运营要看「又被举报了」），
 * `parts.report` 用的是 `item_audit` 的进队快照（权重不跳变）。见 {@link WeightPartsVO}。
 *
 * <p>`itemTints`（前端骨架期的占位渐变）这里**不返回**：已有真图 `mainImage` 与 `images`。
 *
 * <p>`evidence` 是原始 JSON 字符串，后端不解析——凭证图的结构由 C 端表单决定，
 * 后端解析了就要跟着前端改，而审核只需要把图展示出来。
 */
@Data
public class ReviewTaskVO {

    /** 单据主键：item_audit.id / report.id / appeal.id */
    private Long id;

    private QueueKindEnum kind;

    private Long itemId;

    private String itemTitle;

    /** 二级分类名。判读要看「分类与描述是否对得上」，队列不分页也就没有单独的详情接口，只能这里带 */
    private String categoryName;

    /** 商品描述。判读四项（图片 / 标题描述 / 价格 / 分类成色）里的那一项 */
    private String description;

    /** 主图，Drawer 与列表共用；演示数据是外链，加载失败由前端降级 */
    private String mainImage;

    /** 商品图（按 sort 升序），Drawer 取证用 */
    private List<ItemImageVO> images;

    private BigDecimal itemPrice;

    /** 同类目已成交均价；无成交为 0 */
    private BigDecimal categoryAvgPrice;

    private Long sellerId;

    private String sellerNick;

    private Integer sellerCredit;

    /** 实时举报数（report 表） */
    private Integer reportCount;

    /** 图片风险分 0–100，取该商品最新一条审核记录的快照；无记录为 0 */
    private Integer riskScore;

    private ItemStatusEnum itemStatus;

    /** 进队时间：SLA 与等待补偿都以此为基准 */
    private LocalDateTime submitAt;

    /** 已等待小时数，前端算 SLA 档位直接用，省一次时钟对齐 */
    private Integer waitHours;

    /** 举报原因 / 申诉理由；商品审核没有 */
    private String reason;

    /** 举报 / 申诉的凭证 JSON，原样透传 */
    private String evidence;

    /** 申诉特有：原处置人 id（F-A4：复审人不得是他） */
    private Long handledBy;

    /** 申诉特有：原处置人姓名 */
    private String handledByName;

    /** 申诉特有：原处置结果文案，如「已强制下架」 */
    private String handledResult;

    private WeightPartsVO parts;

    /** 排序键 = parts 五项之和 */
    private Integer weight;
}

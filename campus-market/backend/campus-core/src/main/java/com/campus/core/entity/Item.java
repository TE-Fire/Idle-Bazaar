package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品（`item`，核心表）。
 *
 * <p><b>无「已预定」状态</b>（business/03 §4.1）：拍下只生成待付款订单，商品不锁定、先付款先得，
 * 付款成功才转「已售出」，其余待付款订单自动取消。占位判据只有付款一个。
 *
 * <p>`version` 是乐观锁：条件更新会变成
 * `UPDATE ... SET version = version + 1 WHERE id = ? AND version = ?`，以影响行数判抢到。
 * 管理端本期不用它（处置是单人操作、无并发），加上注解是为了 C 端付款占位时直接可用。
 *
 * <p>`main_image` 冗余主图：列表页不联 `item_image` 就能出缩略图。
 * 它是 `item_image` 中 `sort = 0` 那条的副本，改图时两处要一起改。
 */
@Data
@Accessors(chain = true)
@TableName("item")
public class Item {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sellerId;

    /** 标题 5–50 字 */
    private String title;

    /** 二级分类（dict_category 中 parent_id != 0 的那些） */
    private Long categoryId;

    /** 成色 A/B/C/D */
    private String grade;

    /** 售价 */
    private BigDecimal price;

    /** 原价，可空。与「同类目成交均价」不是一回事：这是卖家自己填的划线价 */
    private BigDecimal originPrice;

    private Long campusId;

    /** 面交点，可空（可快递 / 私下约） */
    private Long meetPointId;

    /** 描述；成色 D 必须填瑕疵说明（校验在 C 端发布，管理端只读） */
    private String description;

    /** 主图，列表页免查图表 */
    private String mainImage;

    /** 见 {@link com.campus.core.enums.ItemStatusEnum} */
    private Integer status;

    /** 可议价标签（仅展示，无议价流程） */
    private Integer negotiable;

    private Integer deliveryIncluded;

    /** 驳回 / 强制下架原因，详情页与申诉复盘要看 */
    private String rejectReason;

    @Version
    private Integer version;

    private Integer viewCount;

    private Integer favCount;

    /** 审核通过（上架）时间。草稿与待审核为 null */
    private LocalDateTime publishedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

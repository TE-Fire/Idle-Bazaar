package com.campus.core.dto.req;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生端发布/编辑商品。字段范围与 `item` 表一致（标题 5–50 字、价格 > 0）。
 *
 * <p>必填字段（标题/分类/成色/价格/校区）草稿与送审都查——`item` 表这些列 NOT NULL，
 * 草稿同样要落库；草稿豁免的是图片与描述（business/02「稍后补齐」）。
 * 成色 D 必须写瑕疵说明，但该校验在 Service（跨字段，@Valid 的单字段注解表达不了），
 * 见 {@code ItemService#requireGradeFlawDescribed}。{@code images} 是已上传的 URL 列表
 * （上传走独立接口，MinIO 未接入），入参顺序即图序。
 */
@Data
public class ItemPublishReq {

    @NotBlank
    @Size(min = 5, max = 50)
    private String title;

    @NotNull
    private Long categoryId;

    /** A/B/C/D */
    @NotBlank
    @Size(min = 1, max = 1)
    private String grade;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal price;

    private BigDecimal originPrice;

    @NotNull
    private Long campusId;

    private Long meetPointId;

    private String description;

    /** 已上传的图片地址，≤9 张，第一张为主图 */
    @Size(max = 9)
    private List<String> images;

    /** 可议价标签（仅展示，无议价流程） */
    private Integer negotiable;

    private Integer deliveryIncluded;

    /**
     * 暂存草稿标志：true = 落草稿(1)、不进审核队列、豁免图片与描述；空/false = 提交送审。
     *
     * <p>只对草稿态商品有意义：已发布（在售/已下架）商品编辑时忽略它，按普通编辑处理。
     */
    private Boolean draft;
}

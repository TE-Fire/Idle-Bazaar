package com.campus.core.dto.req;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生端发布/编辑商品。
 *
 * <p>字段范围与 `item` 表注释一致（标题 5–50 字、价格 > 0）。
 * `grade` 取值 A–D：成色 D 必须在描述里写明瑕疵，这条由 Service 校验而不是这里——
 * 校验要跨字段，`@Valid` 的单字段注解表达不了「当 grade = D 时 description 必填」。
 * 发布与编辑两个入口都查，原因见 {@code ItemService#requireGradeFlawDescribed}。
 *
 * <p>注意 {@code images} 是 URL 列表而不是文件：图片上传走独立的接口
 * （MinIO 尚未真正接入，见 README 中间件待办），这里只接收已上传的地址并决定排序与主图。
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
}

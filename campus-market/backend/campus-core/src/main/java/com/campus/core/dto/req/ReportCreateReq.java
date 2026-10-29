package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 学生端提交举报。
 *
 * <p>`targetType` 决定 `targetId` 指谁：1 商品、2 用户、3 动态、4 商品评论、5 动态评论，
 * 取值以 {@link com.campus.core.enums.ReportTargetTypeEnum} 为准。
 * `evidence` 是已上传的凭证地址，≤3 张；后端只存 JSON 不做结构解析——
 * 解析了就要跟着前端表单一起改，而审核员只需要看图。
 */
@Data
public class ReportCreateReq {

    /** 1 商品 / 2 用户 / 3 动态 / 4 商品评论 / 5 动态评论，见 `ReportTargetTypeEnum` */
    @NotNull
    private Integer targetType;

    @NotNull
    private Long targetId;

    @NotBlank
    @Size(max = 255)
    private String reason;

    /** 凭证图片地址，≤3 张 */
    @Size(max = 3)
    private List<String> evidence;
}

package com.campus.core.dto.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交评价（`POST /api/order/{id}/review`，notes/13 O18）。
 *
 * <p>**没有 targetId**：被评价人由服务端按「订单的对方」推出。让前端传的话，
 * 差评可以挂到任意人头上——这是评价体系最容易被利用的口子。
 *
 * <p>`tags` 限 3 个且只能取服务端常量表里的词（`ReviewTagEnum`），文案与标签由服务端下发，
 * 前端不许自己编。
 */
@Data
public class UserReviewCreateReq {

    @NotNull(message = "请选择评分")
    @Min(value = 1, message = "评分为 1–5 星")
    @Max(value = 5, message = "评分为 1–5 星")
    private Integer rating;

    @Size(max = 3, message = "评价标签最多选 3 个")
    private List<String> tags;

    @Size(max = 500, message = "评价内容不超过 500 字")
    private String comment;
}

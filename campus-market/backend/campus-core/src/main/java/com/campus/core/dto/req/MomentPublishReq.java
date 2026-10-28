package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 发布动态请求（notes/22 S3）。
 *
 * <p>type=1 纯状态（content / images 至少其一非空）；type=2 分享商品（必须带 itemId）。
 * 长度、图片数量、敏感词在 Service 内校验，这里只做必填约束。
 */
@Data
public class MomentPublishReq {

    /** 1 纯状态 2 分享商品 */
    @NotNull(message = "动态类型不能为空")
    private Integer type;

    private String content;

    /** 图片 URL 数组，≤9 */
    private List<String> images;

    /** type=2 时必填 */
    private Long itemId;
}

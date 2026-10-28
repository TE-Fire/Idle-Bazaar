package com.campus.core.dto.resp;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态卡片（广场 / 个人主页 / 详情通用，notes/22 S3–S6）。
 *
 * <p>作者信息走 {@link UserPubVO}（昵称/头像/信用/认证）；type=2 时带商品摘要，
 * 商品可能已下架，此时 `itemTitle` 为「商品已下架」、其余商品字段为空。
 * `likeCount` / `commentCount` 实时 COUNT；`liked` 是当前查看者是否已赞。
 */
@Data
public class MomentVO {

    private Long id;

    private Long userId;

    private UserPubVO author;

    /** 1 纯状态 2 分享商品 */
    private Integer type;

    private String content;

    private List<String> images;

    private Long itemId;

    private String itemTitle;

    private String itemMainImage;

    private BigDecimal itemPrice;

    private long likeCount;

    private boolean liked;

    private long commentCount;

    /** 0 正常 1 敏感词屏蔽（仅作者自己在个人主页可见） 2 举报下架 */
    private Integer auditStatus;

    private LocalDateTime createTime;
}

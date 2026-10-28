package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 社交举报一行（管理端，notes/22 批次 3）。
 *
 * <p>`targetSummary` 是被举报内容的正文摘要、`targetAuthorId` 是内容作者——
 * 审核员据此判断要不要下架，不必再跳去内容详情页。
 */
@Data
public class SocialReportVO {

    private Long id;

    private Long reporterId;

    private String reporterNick;

    /** 3 动态 / 4 商品评论 / 5 动态评论 */
    private Integer targetType;

    private String targetTypeName;

    private Long targetId;

    /** 被举报内容摘要（超长截断） */
    private String targetSummary;

    /** 被举报内容作者 */
    private Long targetAuthorId;

    private String targetAuthorNick;

    private String reason;

    private List<String> evidence;

    /** 1 待受理 2 处理中 3 已处理 */
    private Integer status;

    /** 1 成立 2 不成立 */
    private Integer result;

    private String handleNote;

    private LocalDateTime createTime;
}

package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 一条评价（个人主页的评价列表、订单详情的对方评价共用）。
 *
 * <p>`tags` 是字符串数组（库里存 JSON），前端按颜色分好评/差评向；
 * 不返回被评价人——列表本身就是「某个人收到的评价」，再带一次 target 只是冗余。
 */
@Data
public class UserReviewVO {

    private Long id;

    private Long orderId;

    private Long reviewerId;

    private String reviewerNick;

    private String reviewerAvatar;

    /** 1–5 星 */
    private Integer rating;

    private List<String> tags;

    private String comment;

    private LocalDateTime createTime;
}

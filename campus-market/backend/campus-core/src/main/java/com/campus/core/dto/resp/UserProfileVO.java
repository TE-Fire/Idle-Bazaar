package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 用户公开主页聚合（notes/22 S2/S4 的宿主数据）。
 *
 * <p>资料与计数分开装：资料来自 `app_user` 单点查，计数来自 ZSET / COUNT，
 * 计数是 long 转字符串由全局 Jackson 处理，前端拿到的是字符串。
 */
@Data
public class UserProfileVO {

    private Long userId;

    private String nickname;

    private String avatarUrl;

    private Integer creditScore;

    /** 认证：0未认证 1审核中 2已认证 3未通过 */
    private Integer authStatus;

    /** 关注数 */
    private long followingCount;

    /** 粉丝数 */
    private long fansCount;

    /** 在售商品数 */
    private long onSaleCount;

    /** 当前查看者是否已关注（自己的主页恒 false） */
    private boolean following;

    /** 是否查看自己的主页（决定关注按钮换成编辑入口） */
    private boolean self;
}

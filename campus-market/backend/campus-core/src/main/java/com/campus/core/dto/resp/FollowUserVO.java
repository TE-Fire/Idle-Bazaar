package com.campus.core.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关注 / 粉丝列表的一条（notes/22 S2）。
 *
 * <p>只带公开资料：昵称、信用分、认证态来自 {@link UserPubVO} 缓存，
 * 手机号 / 真实姓名 / 学号等敏感字段不进来。
 */
@Data
public class FollowUserVO {

    private Long userId;

    private String nickname;

    private Integer creditScore;

    private Integer authStatus;

    /** 当前查看者是否关注了这个人（粉丝列表里用于显示「回关」按钮态） */
    private Boolean following;

    /** 关系建立时间 */
    private LocalDateTime followTime;
}

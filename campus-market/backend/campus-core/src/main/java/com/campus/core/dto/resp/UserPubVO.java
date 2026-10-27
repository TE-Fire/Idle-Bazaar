package com.campus.core.dto.resp;

import com.campus.core.entity.AppUser;
import lombok.Data;

/**
 * 学生公开资料：商品列表 / 详情回填卖家信息，关注与粉丝列表展示用户。
 *
 * <p>刻意裁剪到四个展示字段：缓存要落 Redis，phone / realName / studentNo 不进任何对外载体。
 * 头像本身是公开信息，加进来只影响列表观感，不扩大泄露面。
 */
@Data
public class UserPubVO {

    private Long userId;

    private String nickname;

    private String avatarUrl;

    private Integer creditScore;

    private Integer authStatus;

    public static UserPubVO from(AppUser u) {
        UserPubVO vo = new UserPubVO();
        vo.setUserId(u.getId());
        vo.setNickname(u.getNickname());
        vo.setAvatarUrl(u.getAvatarUrl());
        vo.setCreditScore(u.getCreditScore());
        vo.setAuthStatus(u.getAuthStatus());
        return vo;
    }
}

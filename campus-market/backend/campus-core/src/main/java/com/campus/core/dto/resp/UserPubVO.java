package com.campus.core.dto.resp;

import com.campus.core.entity.AppUser;
import lombok.Data;

/**
 * 学生公开资料：商品列表 / 详情回填卖家信息用（`sellerNick` / `sellerCredit` / `sellerAuthStatus`）。
 *
 * <p>刻意裁剪到三个字段：缓存要落 Redis，phone / realName / studentNo 不进任何对外载体。
 */
@Data
public class UserPubVO {

    private Long userId;

    private String nickname;

    private Integer creditScore;

    private Integer authStatus;

    public static UserPubVO from(AppUser u) {
        UserPubVO vo = new UserPubVO();
        vo.setUserId(u.getId());
        vo.setNickname(u.getNickname());
        vo.setCreditScore(u.getCreditScore());
        vo.setAuthStatus(u.getAuthStatus());
        return vo;
    }
}

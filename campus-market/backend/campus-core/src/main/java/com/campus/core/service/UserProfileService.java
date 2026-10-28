package com.campus.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.resp.UserProfileVO;
import com.campus.core.entity.AppUser;
import com.campus.core.entity.Item;
import com.campus.core.enums.ItemStatusEnum;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.mapper.ItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户公开主页聚合（notes/22 S2）：资料 + 关注 / 粉丝数 + 在售数 + 查看者关注态。
 *
 * <p>纯读聚合，不落缓存——主页一次进页只打这一发，缓冲在 FollowService 的 ZSET 里，
 * 再叠一层缓存失效就得跟昵称头像改动的写路径对齐，不值得。
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final AppUserMapper appUserMapper;

    private final ItemMapper itemMapper;

    private final FollowService followService;

    /** userId 为空看自己；他人主页是公开信息但入口都在登录态内 */
    public UserProfileVO profile(long viewerId, Long targetUserId) {
        long target = targetUserId == null ? viewerId : targetUserId;
        AppUser u = appUserMapper.selectById(target);
        BizAssert.isTrue(u != null && u.getDeleted() == 0, CommonErrorCode.PARAM_ERROR, "用户不存在");

        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(u.getId());
        vo.setNickname(u.getNickname());
        vo.setAvatarUrl(u.getAvatarUrl());
        vo.setCreditScore(u.getCreditScore());
        vo.setAuthStatus(u.getAuthStatus());
        vo.setFollowingCount(followService.countFollowing(target));
        vo.setFansCount(followService.countFans(target));
        vo.setOnSaleCount(itemMapper.selectCount(new LambdaQueryWrapper<Item>()
                .eq(Item::getSellerId, target)
                .eq(Item::getStatus, ItemStatusEnum.ON_SALE.getCode())
                .eq(Item::getDeleted, 0)));
        vo.setFollowing(viewerId != target && followService.isFollowing(viewerId, target));
        vo.setSelf(viewerId == target);
        return vo;
    }
}

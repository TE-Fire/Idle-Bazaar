package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.req.UserLoginReq;
import com.campus.core.dto.req.UserProfileUpdateReq;
import com.campus.core.dto.resp.UserLoginVO;
import com.campus.core.entity.AppUser;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.satoken.MultiAccountConfig;
import com.campus.core.satoken.StpUserUtil;
import com.campus.core.service.cache.UserPubCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 学生端登录（手机号 + 短信验证码）。Sa-Token 只管会话，验证码归 {@link UserSmsService}。
 *
 * <p>不加 @Transactional：登录成功只有一条 last_login_at 更新加建会话，
 * 没有需要同进同退的多次写库。
 *
 * <p>手机号不存在直接报未注册，不做防枚举：演示期账号全部来自种子数据，
 * 联调时需要明确提示；真实产品接入注册流程后再改成统一文案。
 */
@Service
@RequiredArgsConstructor
public class UserAuthService {

    private final AppUserMapper appUserMapper;
    private final UserSmsService userSmsService;
    private final UserPubCache userPubCache;

    /** 登录：查人 → 状态 → 校验码 → 建会话。先查人再验码，未注册能立刻得到明确提示 */
    public UserLoginVO login(UserLoginReq req) {
        String phone = req.getPhone().trim();
        AppUser user = appUserMapper.selectByPhone(phone);
        BizAssert.notNull(user, UserErrorCode.USER_NOT_FOUND);
        BizAssert.isTrue(user.getStatus() == null || user.getStatus() != 2, UserErrorCode.USER_BANNED);

        userSmsService.verifyCode(phone, req.getCode());

        appUserMapper.updateById(new AppUser()
                .setId(user.getId())
                .setLastLoginAt(LocalDateTime.now()));

        StpUserUtil.login(user.getId());

        UserLoginVO vo = toLoginVO(user);
        vo.setTokenName(MultiAccountConfig.USER_TOKEN_NAME);
        vo.setTokenValue(StpUserUtil.getTokenValue());
        return vo;
    }

    /** 当前登录人信息（token 续期以外的用途：前端刷新后重建会话视图） */
    public UserLoginVO currentUser() {
        long userId = StpUserUtil.getLoginIdAsLong();
        AppUser user = appUserMapper.selectById(userId);
        BizAssert.notNull(user, UserErrorCode.USER_NOT_FOUND);
        return toLoginVO(user);
    }

    /**
     * 资料编辑：昵称 / 头像局部更新（notes/20 D3）。
     *
     * <p>改昵称后必须失效公开资料缓存——昵称在 {@code cm:user:pub} 载荷内，
     * 不 evict 则商品列表与详情的卖家昵称滞后一个 TTL。evict 失败只意味着
     * 多滞后一个 TTL，不能让编辑本身失败，故放在更新之后、不抛。
     */
    public UserLoginVO updateProfile(long userId, UserProfileUpdateReq req) {
        String nickname = req.getNickname();
        String avatarUrl = req.getAvatarUrl();
        BizAssert.isTrue(nickname != null || avatarUrl != null, CommonErrorCode.PARAM_ERROR);

        AppUser update = new AppUser().setId(userId);
        if (nickname != null) {
            String trimmed = nickname.trim();
            BizAssert.isTrue(!trimmed.isEmpty() && trimmed.length() <= 64, UserErrorCode.USER_NICKNAME_INVALID);
            update.setNickname(trimmed);
        }
        if (avatarUrl != null) {
            String trimmed = avatarUrl.trim();
            BizAssert.isTrue(!trimmed.isEmpty() && trimmed.length() <= 255, UserErrorCode.USER_AVATAR_INVALID);
            update.setAvatarUrl(trimmed);
        }
        appUserMapper.updateById(update);

        userPubCache.evict(userId);

        AppUser fresh = appUserMapper.selectById(userId);
        BizAssert.notNull(fresh, UserErrorCode.USER_NOT_FOUND);
        return toLoginVO(fresh);
    }

    private UserLoginVO toLoginVO(AppUser user) {
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setAuthStatus(user.getAuthStatus());
        vo.setCreditScore(user.getCreditScore());
        return vo;
    }
}

package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.common.exception.CommonErrorCode;
import com.campus.core.dto.req.UserLoginReq;
import com.campus.core.dto.req.UserProfileUpdateReq;
import com.campus.core.dto.resp.UserLoginVO;
import com.campus.core.entity.AppUser;
import com.campus.core.enums.MailCodeSceneEnum;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.redis.UserRedisKeys;
import com.campus.core.satoken.MultiAccountConfig;
import com.campus.core.satoken.StpUserUtil;
import com.campus.core.service.cache.UserPubCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 学生端登录（notes/26）：验证码登录（手机号 / QQ 邮箱）与密码登录（手机号 / QQ 邮箱）。
 * Sa-Token 只管会话，验证码归 {@link UserCodeService}，密码比对在本类。
 *
 * <p>不加 @Transactional：登录成功只有一条 last_login_at 更新加建会话，
 * 没有需要同进同退的多次写库。
 *
 * <p>账号不存在明确报「未注册」而不做防枚举：学生端登录一直如此，且找回密码与之同口径，
 * 用户能立刻知道该去注册还是该改密码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuthService {

    /** 密码连续错误 5 次锁 10 分钟；锁定期间验证码登录不受影响 */
    private static final int MAX_PWD_FAIL = 5;
    private static final Duration PWD_LOCK_TTL = Duration.ofMinutes(10);

    private final AppUserMapper appUserMapper;
    private final UserCodeService userCodeService;
    private final UserAccountService userAccountService;
    private final UserPubCache userPubCache;
    private final StringRedisTemplate stringRedisTemplate;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 登录：校验入参 → 查人 → 状态 → 凭据 → 建会话。未注册在查人这步就给出明确提示 */
    public UserLoginVO login(UserLoginReq req) {
        String account = UserCodeService.normalize(req.getAccount());
        int type = req.getType() == null ? 0 : req.getType();
        BizAssert.isTrue(type == 1 || type == 2, CommonErrorCode.PARAM_ERROR);
        requireAccountFormat(account);

        AppUser user = userAccountService.findByAccount(account);
        BizAssert.notNull(user, UserAccountService.notFoundCode(account));
        BizAssert.isTrue(user.getStatus() == null || user.getStatus() != 2, UserErrorCode.USER_BANNED);

        if (type == 1) {
            // 验证码按场景隔离：登录页发的码不能拿去改密码
            userCodeService.verify(account, MailCodeSceneEnum.LOGIN, req.getCredential());
        } else {
            verifyPassword(user, req.getCredential());
        }

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

    /**
     * 密码比对 + 失败计数。
     *
     * <p>未设密码单独报 4038：历史用户是验证码登录进来的，从没设过密码，
     * 报「密码错误」会让人一直怀疑自己记错。
     *
     * <p>达上限后拒绝密码登录，但不清空计数器——清了就等于每 5 次解锁一次。
     * 验证码登录不受影响，用户仍有可用入口。
     */
    private void verifyPassword(AppUser user, String rawPassword) {
        BizAssert.notNull(user.getPasswordHash(), UserErrorCode.USER_PASSWORD_NOT_SET);

        String failKey = UserRedisKeys.buildPwdFailKey(user.getId());
        Integer fails = readFailCount(failKey);
        BizAssert.isTrue(fails == null || fails < MAX_PWD_FAIL, UserErrorCode.USER_PASSWORD_LOCKED);

        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            Long n = stringRedisTemplate.opsForValue().increment(failKey);
            if (n != null && n == 1L) {
                // 只给首次失败设过期：每次 INCR 都续期的话，慢慢试就能把窗口无限拖长
                stringRedisTemplate.expire(failKey, PWD_LOCK_TTL);
            }
            if (n != null && n >= MAX_PWD_FAIL) {
                log.warn("[user] 用户 {} 密码连续错误 {} 次，锁定 {} 分钟", user.getId(), n, PWD_LOCK_TTL.toMinutes());
            }
            throw UserErrorCode.USER_PASSWORD_WRONG.ex();
        }

        stringRedisTemplate.delete(failKey);
    }

    /** Redis 故障时放行（DB 里是 BCrypt 摘要，多试几次也无法离线爆破），不因缓存抖动挡住所有登录 */
    private Integer readFailCount(String key) {
        try {
            String v = stringRedisTemplate.opsForValue().get(key);
            return v == null ? null : Integer.valueOf(v);
        } catch (Exception e) {
            log.warn("[user] 读取密码失败计数失败，按未达上限处理", e);
            return null;
        }
    }

    /** 账号格式：手机号或含 @ 的邮箱；不合法直接报「请输入手机号或 QQ 邮箱」 */
    private static void requireAccountFormat(String account) {
        if (UserCodeService.isMail(account)) {
            return;
        }
        BizAssert.isTrue(UserSmsService.isValidPhone(account), UserErrorCode.USER_ACCOUNT_INVALID);
    }

    private UserLoginVO toLoginVO(AppUser user) {
        UserLoginVO vo = new UserLoginVO();
        vo.setUserId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setAuthStatus(user.getAuthStatus());
        vo.setCreditScore(user.getCreditScore());
        vo.setPhone(user.getPhone());
        vo.setEmail(user.getEmail());
        // 只暴露「有没有密码」，摘要永不出库
        vo.setHasPassword(user.getPasswordHash() != null);
        return vo;
    }
}

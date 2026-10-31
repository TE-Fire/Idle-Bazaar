package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.core.dto.req.UserRegisterReq;
import com.campus.core.dto.req.UserResetPasswordReq;
import com.campus.core.entity.AppUser;
import com.campus.core.enums.MailCodeSceneEnum;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.mapper.AppUserMapper;
import com.campus.core.satoken.StpUserUtil;
import com.campus.core.support.QqMail;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 学生端注册与找回密码（notes/26）。
 *
 * <p>与 {@link UserAuthService} 分开：登录是高频只读校验，注册/找回是低频写入，
 * 混在一起会让登录链路背上发邮件、发短信这些无关依赖。
 *
 * <p>两条边界：
 * ① 注册只验邮箱验证码，手机号只做格式与唯一性校验——短信是演示假码，验证它没有意义；
 * ② 找回密码改完强制下线该账号所有会话，旧会话（可能已被盗用）必须失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAccountService {

    private static final int NICK_MIN = 2;
    private static final int NICK_MAX = 16;
    private static final int PWD_MIN = 8;
    private static final int PWD_MAX = 32;

    private final AppUserMapper appUserMapper;
    private final UserCodeService userCodeService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 注册：格式 → 唯一性 → 验证码 → 落库。
     *
     * <p>唯一性判断放在验码之前：填了已注册的手机号/邮箱立刻能改，不必先等一条验证码。
     * 代价是注册场景可枚举账号，与登录提示「未注册」是同一套取舍（学生端不防枚举）。
     */
    public void register(UserRegisterReq req) {
        String phone = req.getPhone() == null ? "" : req.getPhone().trim();
        String email = QqMail.normalize(req.getEmail());
        String nickname = req.getNickname() == null ? "" : req.getNickname().trim();

        BizAssert.isTrue(UserSmsService.isValidPhone(phone), UserErrorCode.USER_PHONE_INVALID);
        BizAssert.isTrue(QqMail.isQqMail(email), UserErrorCode.EMAIL_INVALID);
        BizAssert.isTrue(nickname.length() >= NICK_MIN && nickname.length() <= NICK_MAX,
                UserErrorCode.USER_NICKNAME_LENGTH_INVALID);
        requirePassword(req.getPassword());

        BizAssert.isTrue(appUserMapper.selectByPhone(phone) == null, UserErrorCode.USER_PHONE_REGISTERED);
        BizAssert.isTrue(appUserMapper.selectByEmail(email) == null, UserErrorCode.EMAIL_ALREADY_REGISTERED);

        userCodeService.verify(email, MailCodeSceneEnum.REGISTER, req.getMailCode());

        AppUser user = new AppUser()
                .setPhone(phone)
                .setEmail(email)
                .setNickname(nickname)
                .setPasswordHash(passwordEncoder.encode(req.getPassword()))
                .setAuthStatus(0)
                .setCreditScore(100)
                .setStatus(0);
        try {
            appUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发下唯一索引兜底：两个请求同时通过上面的查重时只能有一个插进去
            throw UserErrorCode.USER_PHONE_REGISTERED.ex();
        }
        log.info("[user] 新用户注册：{}（id={}）", email, user.getId());
    }

    /**
     * 找回密码：定位账号 → 判重 → 校验验证码 → 改密 → 踢下线。
     *
     * <p>「新密码不能与旧密码相同」必须在校验验证码之前：验证码一次性，
     * 先校验再判重的话，用户输个重复密码就把码用掉了，还得再等 60 秒。
     */
    public void resetPassword(UserResetPasswordReq req) {
        String account = UserCodeService.normalize(req.getAccount());
        requirePassword(req.getPassword());

        AppUser user = findByAccount(account);
        BizAssert.notNull(user, notFoundCode(account));

        if (user.getPasswordHash() != null
                && passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw UserErrorCode.PASSWORD_SAME_AS_OLD.ex();
        }

        userCodeService.verify(account, MailCodeSceneEnum.RESET, req.getCode());

        appUserMapper.updateById(new AppUser()
                .setId(user.getId())
                .setPasswordHash(passwordEncoder.encode(req.getPassword())));

        // 改密后强制下线：可能已被盗用的旧会话必须失效
        StpUserUtil.stpLogic.kickout(user.getId());
        log.info("[user] 账号 {} 已重置密码，旧会话全部下线", account);
    }

    /** 按账号形态查人：手机号查 phone、含 @ 的查 email */
    public AppUser findByAccount(String account) {
        String value = UserCodeService.normalize(account);
        if (UserCodeService.isMail(value)) {
            return appUserMapper.selectByEmail(value);
        }
        return appUserMapper.selectByPhone(value);
    }

    /** 账号不存在时的错误码按渠道给：手机号与邮箱的提示语不同，混用会让人以为填错了 */
    public static UserErrorCode notFoundCode(String account) {
        return UserCodeService.isMail(account) ? UserErrorCode.EMAIL_NOT_REGISTERED : UserErrorCode.USER_NOT_FOUND;
    }

    /** 密码规则：8–32 位且不含空格。含空格时前后截断会不一致，直接不让设 */
    private static void requirePassword(String password) {
        String pwd = password == null ? "" : password;
        BizAssert.isTrue(pwd.length() >= PWD_MIN && pwd.length() <= PWD_MAX && !pwd.contains(" "),
                UserErrorCode.USER_PASSWORD_INVALID);
    }
}

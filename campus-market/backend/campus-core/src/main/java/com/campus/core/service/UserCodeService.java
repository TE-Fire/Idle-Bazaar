package com.campus.core.service;

import com.campus.common.asserts.BizAssert;
import com.campus.core.dto.resp.VerifyCodeVO;
import com.campus.core.enums.MailCodeSceneEnum;
import com.campus.core.errcode.UserErrorCode;
import com.campus.core.support.QqMail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 验证码的渠道分流：一个「获取验证码」入口，按账号形态决定走短信还是 QQ 邮箱。
 *
 * <p>分流只写在这里：登录、注册、找回三处都调它，前端只传 `account`，
 * 「账号是邮箱还是手机号」的判断不会在 Controller 和页面里各写一份。
 */
@Service
@RequiredArgsConstructor
public class UserCodeService {

    private final UserSmsService userSmsService;
    private final UserMailService userMailService;

    public VerifyCodeVO send(String account, MailCodeSceneEnum scene) {
        String value = normalize(account);
        if (isMail(value)) {
            // 非 QQ 域名由 mail 服务统一报 4007，不在这里另立一套提示
            return userMailService.sendCode(value, scene);
        }
        BizAssert.isTrue(UserSmsService.isValidPhone(value), UserErrorCode.USER_ACCOUNT_INVALID);
        return userSmsService.sendCode(value, scene);
    }

    public void verify(String account, MailCodeSceneEnum scene, String code) {
        String value = normalize(account);
        if (isMail(value)) {
            userMailService.verifyCode(value, scene, code);
            return;
        }
        BizAssert.isTrue(UserSmsService.isValidPhone(value), UserErrorCode.USER_ACCOUNT_INVALID);
        userSmsService.verifyCode(value, scene, code);
    }

    /** 账号规范化：去空格 + 转小写（邮箱大小写不敏感，手机号无大小写） */
    public static String normalize(String account) {
        return account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
    }

    /** 含 `@` 即当邮箱处理：这样 163 邮箱会得到「仅支持 QQ 邮箱」而不是「账号格式不正确」 */
    public static boolean isMail(String account) {
        return normalize(account).contains("@");
    }
}

package com.campus.core.errcode;

import com.campus.common.exception.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户模块错误码（段位 40xx，登记于 tech/01 5.5）。
 * C 端用户与管理员同属「用户域」，故管理员登录类错误码也放这里。
 */
@Getter
@AllArgsConstructor
public enum UserErrorCode implements IErrorCode {

    USER_BANNED(4001, "账号已被封禁"),
    USER_AUTH_NOT_PASSED(4002, "学号认证未通过"),
    USER_CREDIT_TOO_LOW(4003, "信用分过低，该操作暂不可用"),
    ADMIN_ACCOUNT_LOCKED(4004, "连续失败次数过多，账号已锁定 10 分钟"),
    ADMIN_BAD_CREDENTIAL(4005, "账号或密码错误"),

    /* ── 注册 / 找回密码（2026-09-29 新增，流程见 business/03 F-A0 / F-A10）── */
    ADMIN_PENDING_ROLE(4006, "账号待超级管理员分配角色，暂不能登录"),
    EMAIL_INVALID(4007, "仅支持 QQ 邮箱（@qq.com / @foxmail.com）"),
    EMAIL_ALREADY_REGISTERED(4008, "该邮箱已注册，可直接登录或找回密码"),
    MAIL_CODE_INVALID(4009, "邮箱验证码不正确或已过期"),
    MAIL_CODE_TOO_FREQUENT(4010, "发送过于频繁，请 60 秒后再试"),
    PASSWORD_SAME_AS_OLD(4011, "新密码不能与旧密码相同"),
    CAPTCHA_INVALID(4012, "图形验证码不正确或已过期"),
    ROLE_SUPER_FORBIDDEN(4013, "超级管理员只能由开发者创建，不支持分配"),

    /* ── 学生端登录（2026-10-21 新增，手机号 + 短信验证码）── */
    USER_PHONE_INVALID(4014, "手机号格式不正确"),
    USER_NOT_FOUND(4015, "该手机号未注册"),
    USER_SMS_TOO_FREQUENT(4016, "发送过于频繁，请 60 秒后再试"),
    USER_SMS_CODE_INVALID(4017, "验证码不正确或已过期"),

    /* ── 询单留言（2026-10-22 新增，business/01/02 同步留言决策）── */
    CHAT_ITEM_NOT_FOUND(4018, "商品不存在"),
    CHAT_PEER_INVALID(4019, "只能与商品卖家询单"),

    /* ── 资料编辑（2026-10-25 新增，业务规则见 notes/20 D3）── */
    USER_NICKNAME_INVALID(4020, "昵称不能为空，且不超过 64 个字符"),
    USER_AVATAR_INVALID(4021, "头像地址不合法"),

    /* ── 社交：动态 / 评论（2026-10-28 新增，notes/22 S3–S7）── */
    MOMENT_NOT_FOUND(4022, "动态不存在或已下架"),
    MOMENT_ITEM_INVALID(4023, "分享的商品不存在或不在售"),
    SENSITIVE_WORD_HIT(4024, "内容含有敏感词，请修改后重试"),
    ITEM_NOT_COMMENTABLE(4025, "商品当前不可评论（仅在售商品可评）"),

    /* ── 社交：敏感词库维护（2026-10-30 新增，管理端 A9）── */
    SENSITIVE_WORD_EXISTS(4026, "该敏感词已存在"),
    SENSITIVE_WORD_NOT_FOUND(4027, "敏感词不存在或已删除"),

    /** 学号认证（notes/20 D5）：学号唯一性只在审核通过时校验，提交阶段放行以便自助纠错 */
    AUTH_ALREADY_VERIFIED(4028, "已完成学号认证，无需重复提交"),
    AUTH_PENDING_EXISTS(4029, "认证申请正在审核中，请耐心等待"),
    STUDENT_NO_TAKEN(4030, "该学号已被其他账号认证"),
    AUTH_APPLY_NOT_FOUND(4031, "认证申请不存在"),
    AUTH_NOT_PENDING(4032, "该申请已处理，请刷新列表"),
    CREDIT_ADJUST_INVALID(4033, "信用分调整幅度需在 −20 到 +20 之间且理由必填"),

    /** 平台公告（notes/22 S12）：5000 是防误触「全员公告」的闸门，不是性能上限 */
    NOTICE_AUDIENCE_TOO_LARGE(4034, "接收人数超过 5000 上限，请缩小范围后分批发布"),
    ;

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }

    private final int code;
    private final String msg;
}

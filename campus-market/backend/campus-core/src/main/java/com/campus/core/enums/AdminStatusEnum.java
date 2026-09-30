package com.campus.core.enums;

import lombok.Getter;

/**
 * 管理员账号状态（对应 `admin_user.status`）。
 *
 * <p>三值在登录链路上报**不同错误码**：停用按「账号或密码错误」返回（不暴露停用事实），
 * 待分配角色返回 ADMIN_PENDING_ROLE——「注册完登录被拦」是设计而非故障，
 * 提示成密码错误会让人以为注册失败了。
 */
@Getter
public enum AdminStatusEnum {

    DISABLED(0, "停用"),
    ENABLED(1, "启用"),
    /** 注册产生，超管分配角色后转 1，此前登录被拦 */
    PENDING_ROLE(2, "待分配角色");

    private final Integer code;
    private final String desc;

    AdminStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按库里的值取枚举。取不到说明传了不可能出现的值，属编码错误，
     * 直接抛 {@link IllegalArgumentException}——包成业务错误码会让这种 bug 被当正常分支处理掉。
     */
    public static AdminStatusEnum fromCode(Integer code) {
        if (code != null) {
            for (AdminStatusEnum status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("未知的管理员状态：" + code);
    }

    public boolean isEnabled() {
        return this == ENABLED;
    }

    public boolean isDisabled() {
        return this == DISABLED;
    }

    public boolean isPendingRole() {
        return this == PENDING_ROLE;
    }
}

package com.campus.core.enums;

import lombok.Getter;

/**
 * 管理员账号状态（对应 `admin_user.status`）。
 *
 * <p>三个取值在登录链路上走的是**不同分支、报不同错误码**，不是可有可无的枚举化：
 * 停用（0）按「账号或密码错误」返回，待分配角色（2）返回 ADMIN_PENDING_ROLE——
 * 后者是因为「注册完登录被拦」是设计而非故障，若也提示成密码错误，
 * 用户会以为自己注册失败了。写成 `status != 2` 的字面量判断，读代码的人
 * 必须回 `schema.sql` 看注释才知道 2 是什么。
 *
 * <p>取值与 `schema.sql` 的字段注释、`db/01-数据库设计.md` §三 一致。
 */
@Getter
public enum AdminStatus {

    /** 0 停用：超管手动停用，登录按「账号或密码错误」处理，不暴露停用事实 */
    DISABLED(0, "停用"),

    /** 1 启用：正常可登录 */
    ENABLED(1, "启用"),

    /** 2 待分配角色：注册产生，超管分配角色后转 1，此前登录被拦 */
    PENDING_ROLE(2, "待分配角色");

    /** 落库的值，与 `admin_user.status` 一致 */
    private final Integer code;

    /** 中文文案，用于日志与后台展示；给前端的文案仍走字典下发 */
    private final String desc;

    AdminStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按数据库里存的值取枚举。
     *
     * <p>名字不用 `of` / `valueOf`：调用处 `AdminStatus.fromCode(admin.getStatus())` 要能
     * 一眼看出「拿库里的 code 换枚举」，而不是像在做什么业务转换。
     *
     * <p>取不到说明传入了库里不可能出现的值，属于**编码错误**而不是业务错误，
     * 所以直接抛 {@link IllegalArgumentException}，不去包一层业务错误码——
     * 包了反而会让这种 bug 被当成正常业务分支处理掉。
     */
    public static AdminStatus fromCode(Integer code) {
        if (code != null) {
            for (AdminStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
        }
        throw new IllegalArgumentException("未知的管理员状态：" + code);
    }

    /** 正常可登录 */
    public boolean isEnabled() {
        return this == ENABLED;
    }

    public boolean isDisabled() {
        return this == DISABLED;
    }

    /** 已注册但还没分配角色，需超管在 A9 处理 */
    public boolean isPendingRole() {
        return this == PENDING_ROLE;
    }
}

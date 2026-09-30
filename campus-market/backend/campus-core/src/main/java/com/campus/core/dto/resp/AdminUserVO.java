package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 管理员列表项（A9 系统设置 · 分配角色用）。
 *
 * <p>**不含 passwordHash / failCount / lockUntil**：这些字段一旦出现在列表接口里，
 * 就等于把密码哈希与爆破进度暴露给任何持有 settings.edit 的人。
 */
@Data
public class AdminUserVO {

    /** 管理员 id */
    private String id;

    private String username;

    private String email;

    private String realName;

    private String roleId;

    private String roleCode;

    /** 1 启用 / 0 停用 / 2 待分配角色 */
    private Integer status;

    private String createTime;
}

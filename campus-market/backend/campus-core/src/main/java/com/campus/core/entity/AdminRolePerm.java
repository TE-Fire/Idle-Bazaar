package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色 × 权限点，行式存储（一个权限点一行）。
 *
 * <p>不存 JSON 的理由：管理端 A9 要渲染权限矩阵（行=权限点、列=角色），
 * 行式可直接查；且 idx_perm 支持「哪些角色有 user.ban」这类反查。
 */
@Data
@TableName("admin_role_perm")
public class AdminRolePerm {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long roleId;

    /** 与前端 data-perm、后端 PermCodes 常量三方同名 */
    private String permCode;
}

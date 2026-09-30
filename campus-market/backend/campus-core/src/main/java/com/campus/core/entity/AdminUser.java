package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 管理员（管理端 RBAC 的「人」）。一人一角色，role_id 单列，不需要中间关联表。
 *
 * <p>`@Accessors(chain = true)`：setter 返回 this，多字段构建写链式；
 * Req/VO 用 `@Builder`，Entity 统一用链式。
 */
@Data
@Accessors(chain = true)
@TableName("admin_user")
public class AdminUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    /**
     * 邮箱（注册 / 找回密码用，QQ 系：@qq.com、@foxmail.com）。
     * 唯一索引 uk_email(email, deleted)，所以查邮箱时必须显式带 deleted=0。
     */
    private String email;

    /** BCrypt 哈希，不存明文 */
    private String passwordHash;

    private String realName;

    private Long roleId;

    /** 1 启用 / 0 停用 / 2 待分配角色（注册产生，登录被拦） */
    private Integer status;

    /** 连续失败次数，5 次锁 10 分钟 */
    private Integer failCount;

    private LocalDateTime lockUntil;

    private LocalDateTime lastLoginAt;

    /** 审计三件套由 AuditMetaObjectHandler 自动填充，业务代码不再手动 set */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0 未删，非 0 为删除时间戳 */
    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

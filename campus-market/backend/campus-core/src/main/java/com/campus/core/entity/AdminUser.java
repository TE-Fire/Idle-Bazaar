package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员（管理端 RBAC 的「人」）。
 * 一人一角色：role_id 单列，不需要中间关联表。
 */
@Data
@TableName("admin_user")
public class AdminUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    /** BCrypt 哈希，不存明文 */
    private String passwordHash;

    private String realName;

    private Long roleId;

    /** 1 启用 / 0 停用 */
    private Integer status;

    /** 连续失败次数，5 次锁 10 分钟 */
    private Integer failCount;

    private LocalDateTime lockUntil;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 逻辑删除：0 未删，非 0 为删除时间戳 */
    private Long deleted;
}

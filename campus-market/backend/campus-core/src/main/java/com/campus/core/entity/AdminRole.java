package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端角色：SUPER / REVIEWER / OPERATOR / READONLY
 */
@Data
@TableName("admin_role")
public class AdminRole {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String roleCode;

    private String roleName;

    private Integer sort;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long deleted;
}

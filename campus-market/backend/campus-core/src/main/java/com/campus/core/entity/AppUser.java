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
 * 学生用户（`app_user`）。
 *
 * <p>本模块只需要 `nickname` 与 `credit_score`（商品列表的卖家两列），
 * 但实体照表写全字段——用户管理（A-B5）与认证审核（F-A5）要用同一份映射，
 * 缺字段时再去补会让两个模块对同一张表有两种认知。
 *
 * <p>`real_name` / `student_no` / `phone` 属敏感信息，只在认证审核链路内使用，
 * 不要进任何对外 VO（F-A11 判据变更前后的值也要脱敏）。
 */
@Data
@Accessors(chain = true)
@TableName("app_user")
public class AppUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String openid;

    private String nickname;

    private String avatarUrl;

    /** 手机号（Web 端验证码登录） */
    private String phone;

    /** 真实姓名（认证后写入，不公开展示） */
    private String realName;

    /** 学号（不公开展示） */
    private String studentNo;

    private Long collegeId;

    private Long campusId;

    /** 认证：0未认证 1审核中 2已认证 3未通过 */
    private Integer authStatus;

    /** 信用分 0–120，默认 100，<60 强制人工审核 */
    private Integer creditScore;

    /** 账号：0正常 1警告 2封禁 */
    private Integer status;

    private LocalDateTime banUntil;

    private String banReason;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

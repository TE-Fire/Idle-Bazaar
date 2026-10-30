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
 * 学号认证申请（`user_auth`），一对多：被驳回后可重提，每次重提新增一行。
 *
 * <p>A 类软删（留墓碑）：审核历史本身有价值，管理端要能回答「提交过几次、前几次为何被驳回」。
 */
@Data
@Accessors(chain = true)
@TableName("user_auth")
public class UserAuth {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private String realName;

    private String studentNo;

    private Long collegeId;

    /** 一卡通或校园邮箱截图 URL */
    private String credentialUrl;

    /** 1审核中 2通过 3未通过 */
    private Integer status;

    private String rejectReason;

    private Long handlerId;

    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

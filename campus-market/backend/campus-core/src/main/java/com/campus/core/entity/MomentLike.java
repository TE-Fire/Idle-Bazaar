package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态点赞（`moment_like`）。
 *
 * <p>B 类软删：唯一键 (moment_id, user_id, deleted)，取消点赞写时间戳、再赞复用同一行（见 MomentLikeMapper）。
 */
@Data
@TableName("moment_like")
public class MomentLike {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long momentId;

    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 0 未删；>0 删除时间戳（毫秒） */
    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

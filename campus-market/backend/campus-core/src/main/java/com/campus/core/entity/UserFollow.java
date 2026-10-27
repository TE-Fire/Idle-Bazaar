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
 * 关注关系（`user_follow`）。
 *
 * <p>B 类软删：唯一键是 (follower_id, followee_id, deleted)，取关把 deleted 写成毫秒时间戳，
 * 重新关注把同一行置回 0——每对关系恒一行，见 db/01「删除的两类实现」。
 */
@Data
@Accessors(chain = true)
@TableName("user_follow")
public class UserFollow {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 关注者 */
    private Long followerId;

    /** 被关注者 */
    private Long followeeId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 0 未删；>0 为删除时间戳（毫秒） */
    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

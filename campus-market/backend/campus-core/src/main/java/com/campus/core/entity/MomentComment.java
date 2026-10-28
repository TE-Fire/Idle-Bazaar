package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论（`moment_comment`）。
 *
 * <p>A 类留墓碑；`reply_to` 存目标用户 id（扁平列表只 @人，不定位父楼）；`audit_status` 同 user_moment。
 */
@Data
@TableName("moment_comment")
public class MomentComment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long momentId;

    private Long userId;

    /** 回复目标用户 id，可空 */
    private Long replyTo;

    private String content;

    /** 0 正常 1 敏感词屏蔽 2 举报下架 */
    private Integer auditStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

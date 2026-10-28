package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品评论（`item_comment`）。
 *
 * <p>A 类留墓碑；`reply_to` 存目标用户 id（扁平列表只 @人）；仅在售商品可新增，下架后历史评论仍可见。
 */
@Data
@TableName("item_comment")
public class ItemComment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long itemId;

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

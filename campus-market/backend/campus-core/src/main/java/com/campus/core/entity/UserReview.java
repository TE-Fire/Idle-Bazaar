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
 * 交易互评（`user_review`，notes/13 O18）。
 *
 * <p>一单一评由唯一键 `(order_id, reviewer_id, deleted)` 兜住：买卖双方各最多一条，
 * `target_id` 由服务端按「对方」推出，不接受前端传被评价人。
 */
@Data
@Accessors(chain = true)
@TableName("user_review")
public class UserReview {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    private Long reviewerId;

    private Long targetId;

    /** 1–5 星 */
    private Integer rating;

    /** 标签 JSON 数组串（服务端常量表里的词，最多 3 个） */
    private String tags;

    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

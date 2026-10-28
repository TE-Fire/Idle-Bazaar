package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户动态（`user_moment`，notes/22 S3–S6）。
 *
 * <p>A 类留墓碑：删除是 `deleted` 置时间戳的新行语义（不物理删）；`audit_status` 0 正常 / 1 敏感词屏蔽 / 2 举报下架。
 * `images` 为 JSON 数组列，实体先以 String 承载 JSON 文本，序列化/反序列化在 Service 内做。
 */
@Data
@TableName("user_moment")
public class UserMoment {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 作者 */
    private Long userId;

    /** 1 纯状态 2 分享商品 */
    private Integer type;

    private String content;

    /** 图片 URL 数组的 JSON 文本（≤9） */
    private String images;

    /** type=2 时关联商品 */
    private Long itemId;

    /** 0 正常 1 敏感词屏蔽 2 举报下架 */
    private Integer auditStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

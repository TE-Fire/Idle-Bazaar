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
 * 敏感词（`sys_sensitive_word`）。
 *
 * <p>level=1 高危（发布直接拒）、level=2 低危（替换后入库）；status=1 启用。
 * 过滤入口见 {@code SensitiveWordService}，与商品描述共用同一词库。
 */
@Data
@Accessors(chain = true)
@TableName("sys_sensitive_word")
public class SensitiveWord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String word;

    /** 1 高危（直接拒绝）2 低危（替换并打标） */
    private Integer level;

    /** 1 启用 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

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
 * 收藏关系（`favorite`）。
 *
 * <p>B 类软删：唯一键 (user_id, item_id, deleted)，取消把 deleted 写成毫秒时间戳，
 * 重新收藏复用同一行置 0——每对关系恒一行，见 db/01「删除的两类实现」。
 */
@Data
@Accessors(chain = true)
@TableName("favorite")
public class Favorite {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long itemId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 0 未删；>0 为删除时间戳（毫秒） */
    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

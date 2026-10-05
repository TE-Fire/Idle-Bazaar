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
 * 商品图（`item_image`，≤9 张）。
 *
 * <p>`sort = 0` 是主图，与 `item.main_image` 指向同一张——前者用于详情轮播，
 * 后者是列表页免查图表的冗余字段。
 */
@Data
@Accessors(chain = true)
@TableName("item_image")
public class ItemImage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long itemId;

    private String url;

    /** 排序，0 为主图 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

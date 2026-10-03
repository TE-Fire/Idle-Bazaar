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
 * 商品分类树（**只允许两级**）
 *
 * <p>一级的 `parentId` 存 **0** 而不是 NULL——`idx_parent(parent_id, sort)` 才能命中，
 * 且避免 `parent_id is null` 这种不走索引的写法。
 * 转成 VO 时再把 0 换成前端约定的 `null`（前端 `DictCategoryItem.parentId` 一级为 null）。
 *
 * <p>为什么不做三级：分类是给学生扫一眼用的，三级会让人在发布页里迷路；
 * 真需要细分靠商品标题与搜索兜住。
 */
@Data
@Accessors(chain = true)
@TableName("dict_category")
public class DictCategory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 父分类 id，一级为 0 */
    private Long parentId;

    private String name;

    /** 图标 URL；骨架期可以为空，前端有兜底 */
    private String icon;

    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

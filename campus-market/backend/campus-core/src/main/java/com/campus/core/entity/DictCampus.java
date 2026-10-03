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
 * 校区字典（db/schema.sql 五张字典之一，business/03 F-A7）
 *
 * <p>五张字典是**全端一致性的源头**：分类、成色、面交点、学院、校区都由后台下发，
 * 三端（Web / 小程序 / 管理端）不得本地硬编码文案与颜色。
 *
 * <p>停用用 `status`（1 启用 / 0 停用）而不是删除：字典被商品引用着，
 * 真删会留下孤儿数据。
 */
@Data
@Accessors(chain = true)
@TableName("dict_campus")
public class DictCampus {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private Integer sort;

    /** 1 启用 / 0 停用 */
    private Integer status;

    /** 审计三件套由 AuditMetaObjectHandler 自动填充，业务代码不要手动 set */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除：0 未删，非 0 为删除时间戳 */
    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

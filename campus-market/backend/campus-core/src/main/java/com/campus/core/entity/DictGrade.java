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
 * 成色等级 A–D（全端共用）
 *
 * <p>`code` 是业务主键（A/B/C/D），`id` 只是雪花主键：前端按 code 取色与文案，
 * 重复由唯一索引 `uk_code(code, deleted)` 拦截。
 *
 * <p>`color` 由后台下发（`db/01` §4.9 判据：成色带文案与颜色，前端不写死色值），
 * 2026-09-30 从前端 mock 收进表里，改颜色不用发版。
 */
@Data
@Accessors(chain = true)
@TableName("dict_grade")
public class DictGrade {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** A / B / C / D */
    private String code;

    /** 成色名称，如「几乎全新」 */
    private String name;

    /** 徽章色值，如 #34C759；前端直接用，不硬编码 */
    private String color;

    /** 成色说明 */
    private String description;

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

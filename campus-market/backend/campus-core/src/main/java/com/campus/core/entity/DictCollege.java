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
 * 学院字典（报表「学院 × 品类」矩阵的行）
 *
 * <p>`campusId` 指向 `dict_campus`：**校区 → 学院** 两级，报表按校区拆行要用
 * （2026-10-02 补列，`idx_campus(campus_id, status)` 与 `dict_meet_point` 同构）。
 * `0` 表示未指定——与 `dict_category` 一级存 0 同一套路，VO 里再转成 null。
 */
@Data
@Accessors(chain = true)
@TableName("dict_college")
public class DictCollege {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属校区；0 = 未指定 */
    private Long campusId;

    private String name;

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

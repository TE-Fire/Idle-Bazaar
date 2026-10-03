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
 * 校内面交点（报表面交所热力的维度）
 *
 * <p>`campusId` 指向 `dict_campus`：**校区停用时它下面的面交点要一并处理**
 * （前端停用校区时会提示，Service 也要给出同样的约束），这是字典之间的引用约束。
 */
@Data
@Accessors(chain = true)
@TableName("dict_meet_point")
public class DictMeetPoint {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属校区 */
    private Long campusId;

    private String name;

    /** 开放时段文案，如「08:00–22:00」；直接展示，不做结构化 */
    private String openHours;

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

package com.campus.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 信用分变动流水（`credit_log`）：只追加不改写，每次变动记变动前后值。
 *
 * <p>`reason_type` 取 {@link com.campus.core.enums.CreditReasonEnum} 的名称；
 * `biz_id` 指向触发变动的业务单据（举报单 id 等），用于追溯与防重复扣分。
 */
@Data
@TableName("credit_log")
public class CreditLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /** 变动值（正/负） */
    private Integer changeValue;

    private Integer beforeScore;

    private Integer afterScore;

    private String reasonType;

    private Long bizId;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

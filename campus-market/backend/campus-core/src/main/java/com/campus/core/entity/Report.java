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
 * 举报（`report`，A-B3）。
 *
 * <p>`target_type` 决定 `target_id` 指谁：1 商品 → `item.id`，2 用户 → `app_user.id`。
 * 本期队列只处理 1（演示数据 6 条全是商品举报），用户举报留待 A-B5 用户管理。
 * 查询时一律带 `target_type = 1`，别只按 `target_id` 查——两个域的 id 空间是重叠的雪花 ID。
 *
 * <p>`evidence` 是 JSON 字符串（≤3 张凭证图），原样透传给前端展示；
 * 后端不解析它的结构——解析了就要跟着 C 端的表单改，而审核员只需要看图。
 *
 * <p>「处理中」(2) 这个状态在单人审核场景里没有意义（没人会把单子挂起来等别人接），
 * 所以本期不做独立的「受理」接口：点开处置即视为受理，裁决时一步到位写 3（notes/09 问题 4）。
 */
@Data
@Accessors(chain = true)
@TableName("report")
public class Report {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 举报人 `app_user.id` */
    private Long reporterId;

    /** 1商品 2用户 */
    private Integer targetType;

    /** 被举报对象 id，语义由 targetType 决定 */
    private Long targetId;

    private String reason;

    /** 凭证图片 JSON，≤3 张 */
    private String evidence;

    /** 见 {@link com.campus.core.enums.ReportStatusEnum} */
    private Integer status;

    /** 1成立 2不成立，仅在 status = 3 时有值 */
    private Integer result;

    /** 处置说明（通知双方） */
    private String handleNote;

    private Long handlerId;

    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableField(fill = FieldFill.INSERT)
    private Long deleted;
}

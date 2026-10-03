package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 成色等级（对齐前端 `DictGradeItem`）
 *
 * <p>字段名按**前端契约**命名（`label` / `desc`），而不是照实体叫 `name` / `description`：
 * 前端已经有一套类型定义，VO 迁就前端比让前端改类型省事，也避免两端各写一份映射。
 *
 * <p>`color` 由后台下发（db/01 §4.9 判据），前端直接用，不硬编码色值。
 */
@Data
public class DictGradeVO {

    /** A / B / C / D */
    private String code;

    private String label;

    private String desc;

    /** #RRGGBB */
    private String color;

    private Boolean enabled;
}

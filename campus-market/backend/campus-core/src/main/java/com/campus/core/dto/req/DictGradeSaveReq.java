package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 成色等级新增 / 编辑入参（按 `code` 定位，不按 id）
 *
 * <p>成色是**固定四档**（A/B/C/D），业务主键是 `code` 而不是雪花 id：
 * 前端按 code 取色值与文案，接口也按 code 改（重复由唯一索引 `uk_code` 拦截）。
 *
 * <p>`color` 用正则而不是"随便填"：色值会被前端直接喂给 CSS，
 * 放进来的如果是 `javascript:` 之类就是 XSS 面；`#RRGGBB` 七字符定长最省心。
 */
@Data
public class DictGradeSaveReq {

    /** A / B / C / D */
    @NotBlank(message = "成色编码不能为空")
    @Pattern(regexp = "^[A-D]$", message = "成色编码只能是 A/B/C/D")
    private String code;

    @NotBlank(message = "成色文案不能为空")
    @Size(max = 16, message = "成色文案不超过 16 字")
    private String name;

    /** 徽章色值，如 #34C759；为空时取 #8E8E93 */
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "色值需为 #RRGGBB 格式")
    private String color;

    @Size(max = 255, message = "成色说明不超过 255 字")
    private String description;

    private Integer sort;
}

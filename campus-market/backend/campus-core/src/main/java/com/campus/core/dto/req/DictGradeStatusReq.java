package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 成色启停入参（按 `code` 定位）
 *
 * <p>四档成色是固定集合，停用某一档等于"不允许再选这个成色"，
 * 与 `DictStatusReq` 语义相同、定位方式不同，所以单独一个类而不是硬凑共用。
 */
@Data
public class DictGradeStatusReq {

    @NotBlank(message = "成色编码不能为空")
    @Pattern(regexp = "^[A-D]$", message = "成色编码只能是 A/B/C/D")
    private String code;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}

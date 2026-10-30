package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 敏感词启停入参。
 *
 * <p>与 `DictStatusReq` 同形但不复用：那个类绑着五张字典表的语义，
 * 共用会让日后任一侧加字段时被迫一起改。改哪一条由 URL 路径的 `{id}` 决定。
 */
@Data
public class SensitiveWordStatusReq {

    /** true 启用 / false 停用 */
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}

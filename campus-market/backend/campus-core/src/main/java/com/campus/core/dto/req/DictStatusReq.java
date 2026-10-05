package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 字典启停入参（五张表共用）
 *
 * <p>只有 `enabled` 一个字段：改哪一条由 URL 路径决定（四张表用 `{id}`，成色用 `{code}`），
 * body 里再带一个 id / code 就是同一份数据两个来源。
 *
 * <p>为什么是 `enabled` 而不是 `status`：DTO 是**接口契约**，跟前端对齐
 * （前端 `enabled: boolean`）；实体里的 `status`（1/0）是存储口径，转换放在 Service。
 */
@Data
public class DictStatusReq {

    /** true 启用 / false 停用 */
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}

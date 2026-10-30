package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 敏感词新增 / 编辑入参。
 *
 * <p>不含 `status`：启停是独立动作（`/admin/sensitive/{id}/status`），
 * 编辑时把开关一起改容易在「只是改级别」的请求里把停用词顺手启用。
 */
@Data
public class SensitiveWordSaveReq {

    @NotBlank(message = "敏感词不能为空")
    @Size(max = 64, message = "敏感词不超过 64 字")
    private String word;

    /** 1 高危 / 2 低危；为空按 2（低危）处理——误把新词设成高危会直接拦发布 */
    private Integer level;
}

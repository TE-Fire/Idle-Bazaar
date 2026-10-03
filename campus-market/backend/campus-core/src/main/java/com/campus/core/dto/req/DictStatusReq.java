package com.campus.core.dto.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 字典启停入参（分类 / 面交点 / 学院 / 校区共用）
 *
 * <p>成色按 `code` 定位，走 `DictGradeStatusReq`；其余四张表按 `id` 定位，走本类。
 * 分成两个而不是一个"二选一"的类：共用类会退化成「id 和 code 都可选，靠注释说明填哪个」，
 * 编译期拦不住填错，是典型的把校验成本转嫁给运行期。
 *
 * <p>为什么是 `enabled` 而不是 `status`：DTO 是**接口契约**，跟前端对齐
 * （前端 `enabled: boolean`）；实体里的 `status`（1/0）是存储口径，转换放在 Service。
 */
@Data
public class DictStatusReq {

    @NotNull(message = "字典 id 不能为空")
    private String id;

    /** true 启用 / false 停用 */
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;
}

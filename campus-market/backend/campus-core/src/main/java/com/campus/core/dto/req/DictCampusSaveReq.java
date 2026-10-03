package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 校区新增 / 编辑入参
 *
 * <p><b>没有 `id` 字段</b>：新增走 `POST /admin/dict/campus`，编辑走
 * `PUT /admin/dict/campus/{id}`——**改哪一条由 URL 路径决定**，Service 对应拆成
 * `createCampus(req)` / `updateCampus(id, req)`。
 *
 * <p>校区是引用链的顶端：停用校区时它下面的面交点与学院要一并停用，
 * 这条约束在 Service 里做，不在这个 DTO 里体现（DTO 只管"填了什么"）。
 */
@Data
public class DictCampusSaveReq {

    @NotBlank(message = "校区名称不能为空")
    @Size(max = 32, message = "校区名称不超过 32 字")
    private String name;

    private Integer sort;
}

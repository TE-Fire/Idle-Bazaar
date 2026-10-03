package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学院新增 / 编辑入参
 *
 * <p><b>没有 `id` 字段</b>：新增走 `POST /admin/dict/college`，编辑走
 * `PUT /admin/dict/college/{id}`——**改哪一条由 URL 路径决定**，Service 对应拆成
 * `createCollege(req)` / `updateCollege(id, req)`。
 *
 * <p>`campusId` 可为空 → 落到表里是 0（未指定）：学院可以暂时不挂校区，
 * 但不能挂一个不存在的校区——"存在且启用"这条由 Service 用 `BizAssert` 校验，
 * 声明式注解查不了库。
 */
@Data
public class DictCollegeSaveReq {

    @NotBlank(message = "学院名称不能为空")
    @Size(max = 64, message = "学院名称不超过 64 字")
    private String name;

    /** 所属校区 id，为空表示未指定 */
    private String campusId;

    private Integer sort;
}

package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 面交点新增 / 编辑入参
 *
 * <p><b>没有 `id` 字段</b>：新增走 `POST /admin/dict/meet-point`，编辑走
 * `PUT /admin/dict/meet-point/{id}`——**改哪一条由 URL 路径决定**，Service 对应拆成
 * `createMeetPoint(req)` / `updateMeetPoint(id, req)`。
 *
 * <p>`campusId` 必填：面交点必须挂在某个校区下，Service 校验该校区存在且启用——
 * 停用校区下还留着启用中的面交点，会出现"能选但不能去"的死数据。
 */
@Data
public class DictMeetPointSaveReq {

    @NotBlank(message = "面交点名称不能为空")
    @Size(max = 64, message = "面交点名称不超过 64 字")
    private String name;

    @NotBlank(message = "所属校区不能为空")
    private String campusId;

    /** 开放时段文案，如「08:00–22:00」；直接展示，不结构化存储 */
    @Size(max = 64, message = "开放时段不超过 64 字")
    private String openHours;

    private Integer sort;
}

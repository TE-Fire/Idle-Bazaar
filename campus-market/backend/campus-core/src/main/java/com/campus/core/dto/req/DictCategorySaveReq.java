package com.campus.core.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 分类新增 / 编辑入参
 *
 * <p>没有 `id` 字段：新增走 `POST /admin/dict/category`，编辑走 `PUT /admin/dict/category/{id}`，
 * 改哪一条由 URL 路径决定。body 里再带一个 id 就是同一份数据两个来源，无法确定以谁为准。
 * 对应的 Service 也拆成了 `createCategory(req)` / `updateCategory(id, req)`。
 *
 * <p>`parentId` 为空表示一级分类，非空时由 Service 校验父级存在、启用且本身是一级——
 * 声明式校验管不了这种跨记录的约束。
 *
 * <p>为什么五张表各写各的 Req 而不是一个 `DictSaveReq` 通吃：
 * 字段差异太大（分类有父子、成色有 code+color、面交点有校区+时段），
 * 硬塞进一个类会让每个字段都得靠注释说明"这张表不用它"。
 */
@Data
public class DictCategorySaveReq {

    /** 上级分类 id，为空则是一级分类 */
    private String parentId;

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 32, message = "分类名称不超过 32 字")
    private String name;

    /** 图标 URL，可为空（前端有默认图形） */
    @Size(max = 255, message = "图标地址过长")
    private String icon;

    /** 排序，越小越靠前；为空按 99 处理 */
    private Integer sort;
}

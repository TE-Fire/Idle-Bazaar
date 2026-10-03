package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 学院（对齐前端 `DictCollegeItem`）
 *
 * <p>`campusId` 为 null 表示"未指定校区"——表里存 0，0 → null 的转换在 Service 做，
 * 与分类一级 `parentId` 同一套路。
 */
@Data
public class DictCollegeVO {

    private Long id;

    private String name;

    /** 所属校区；未指定（表里为 0）时为 null */
    private Long campusId;

    /** 冗余校区名，省掉前端二次查询；未指定时为 null */
    private String campusName;

    private Integer sort;

    private Boolean enabled;
}

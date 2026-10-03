package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 校区（对齐前端 `DictCampusItem`）
 *
 * <p>字段最少的一张，但它是引用链的顶端：停用它会影响面交点与学院的归属展示，
 * 所以 `enabled` 在这张表上格外敏感。
 */
@Data
public class DictCampusVO {

    private Long id;

    private String name;

    private Integer sort;

    private Boolean enabled;
}

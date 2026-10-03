package com.campus.core.dto.resp;

import lombok.Data;

/**
 * 分类（对齐前端 `DictCategoryItem`）
 *
 * <p>`parentId` 为 null 表示一级分类——数据库里一级存 0（`idx_parent` 才走得动），
 * 0 → null 的转换在 Service 里做，VO 只对外表达"没有上级"。
 *
 * <p>id 是 Long：Jackson 已全局把 Long 序列化成 String（雪花 ID 精度），前端类型写成 string 即可。
 */
@Data
public class DictCategoryVO {

    private Long id;

    private String name;

    /** 一级分类为 null */
    private Long parentId;

    private Integer sort;

    private Boolean enabled;

    /**
     * 该分类下的在售商品数（前端用于「删除前提示」）。
     * 商品模块落地前**恒为 0**，接口层不要把它当成真实库存。
     */
    private Integer itemCount;
}
